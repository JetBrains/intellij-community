/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarUpdateQueue extends MergingUpdateQueue {
  private AtomicBoolean myModelUpdating = new AtomicBoolean(Boolean.FALSE);
  private Alarm myUserActivityAlarm = new Alarm(this);
  private Runnable myRunWhenListRebuilt;
  private Runnable myUserActivityAlarmRunnable = new Runnable() {
    @Override
    public void run() {
      processUserActivity();
    }
  };

  private NavBarPanel myPanel;

  public NavBarUpdateQueue(NavBarPanel panel) {
    super("NavBar", Registry.intValue("navBar.updateMergeTime"), true, MergingUpdateQueue.ANY_COMPONENT, panel);
    myPanel = panel;
    setTrackUiActivity(true);
    IdeEventQueue.getInstance().addActivityListener(new Runnable() {
        @Override
        public void run() {
          restartRebuild();
        }
      }, panel);
  }

  private void requestModelUpdate(@Nullable final DataContext context, final @Nullable Object object, boolean requeue) {
    if (myModelUpdating.getAndSet(true) && !requeue) return;

    cancelAllUpdates();

    queue(new AfterModelUpdate(ID.MODEL) {
      @Override
      public void run() {
        if (context != null || object != null) {
          requestModelUpdateFromContextOrObject(context, object);
        } else {
          DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
            @Override
            public void consume(DataContext dataContext) {
              requestModelUpdateFromContextOrObject(dataContext, null);
            }
          });
        }
      }

      @Override
      public void setRejected() {
        super.setRejected();
        myModelUpdating.set(false);
      }
    });
  }

  private void requestModelUpdateFromContextOrObject(DataContext dataContext, Object object) {
    final NavBarModel model = myPanel.getModel();
    if (dataContext != null) {
      if (CommonDataKeys.PROJECT.getData(dataContext) != myPanel.getProject() || myPanel.isNodePopupShowing()) {
        requestModelUpdate(null, myPanel.getContextObject(), true);
        return;
      }
      final Window window = SwingUtilities.getWindowAncestor(myPanel);
      if (window != null && !window.isFocused()) {
        model.updateModel(DataManager.getInstance().getDataContext(myPanel));
      } else {
        model.updateModel(dataContext);
      }
    } else {
      model.updateModel(object);
    }

    queueRebuildUi();

    myModelUpdating.set(false);
  }

  void restartRebuild() {
    myUserActivityAlarm.cancelAllRequests();
    if (!myUserActivityAlarm.isDisposed()) {
      myUserActivityAlarm.addRequest(myUserActivityAlarmRunnable, Registry.intValue("navBar.userActivityMergeTime"));
    }
  }

  private void processUserActivity() {
    if (myPanel == null || !myPanel.isShowing()) {
      return;
    }

    final Project project = myPanel.getProject();
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        Window wnd = SwingUtilities.windowForComponent(myPanel);
        if (wnd == null) return;

        Component focus = null;

        if (!wnd.isActive()) {
          IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, myPanel);
          if (frame != null) {
            focus = IdeFocusManager.getInstance(project).getLastFocusedFor(frame);
          }
        }
        else {
          final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
          if (window instanceof Dialog) {
            final Dialog dialog = (Dialog)window;
            if (dialog.isModal() && !SwingUtilities.isDescendingFrom(myPanel, dialog)) {
              return;
            }
          }
        }

        if (focus != null && focus.isShowing()) {
          if (!myPanel.hasFocus() && !myPanel.isNodePopupShowing()) {
            requestModelUpdate(DataManager.getInstance().getDataContext(focus), null, false);
          }
        }
        else if (wnd.isActive()) {
          requestModelUpdate(null, myPanel.getContextObject(), false);
        }
      }
    });
  }

  public void queueModelUpdate(DataContext context) {
   requestModelUpdate(context, null, false);
  }

  public void queueModelUpdateFromFocus() {
    queueModelUpdateFromFocus(false);
  }

  public void queueModelUpdateFromFocus(boolean requeue) {
    requestModelUpdate(null, myPanel.getContextObject(), requeue);
  }

  public void queueModelUpdateForObject(Object object) {
    requestModelUpdate(null, object, false);
  }


  public void queueRebuildUi() {
    queue(new AfterModelUpdate(ID.UI) {
      @Override
      protected void after() {
        rebuildUi();
      }
    });
    queueRevalidate(null);
  }

  public void rebuildUi() {
    if (!myPanel.isRebuildUiNeeded()) return;

    myPanel.clearItems();
    for (int index = 0; index < myPanel.getModel().size(); index++) {
      final Object object = myPanel.getModel().get(index);
      final NavBarItem label = new NavBarItem(myPanel, object, index, null);

      myPanel.installActions(index, label);
      myPanel.addItem(label);

    }

    rebuildComponent();

    if (myRunWhenListRebuilt != null) {
      Runnable r = myRunWhenListRebuilt;
      myRunWhenListRebuilt = null;
      r.run();
    }
  }


  private void rebuildComponent() {
    myPanel.removeAll();

    for (NavBarItem item : myPanel.getItems()) {
      myPanel.add(item);
    }

    myPanel.revalidate();
    myPanel.repaint();

    queueAfterAll(new Runnable() {
      @Override
      public void run() {
        myPanel.scrollSelectionToVisible();
      }
    }, ID.SCROLL_TO_VISIBLE);
  }

  private void queueRevalidate(@Nullable final Runnable after) {
    queue(new AfterModelUpdate(ID.REVALIDATE) {
      @Override
      protected void after() {
        final LightweightHint hint = myPanel.getHint();
        if (hint != null) {
          myPanel.getHintContainerShowPoint().doWhenDone(new Consumer<RelativePoint>() {
            @Override
            public void consume(final RelativePoint relativePoint) {
              hint.setSize(myPanel.getPreferredSize());
              hint.setLocation(relativePoint);
              if (after != null) {
                after.run();
              }
            }
          });
        }
        else {
          if (after != null) {
            after.run();
          }
        }
      }
    });
  }

  void queueSelect(final Runnable runnable) {
    queue(new AfterModelUpdate(NavBarUpdateQueue.ID.SELECT) {
      @Override
      protected void after() {
        runnable.run();
      }
    });
  }

  void queueAfterAll(final Runnable runnable, ID id) {
    queue(new AfterModelUpdate(id) {
      @Override
      protected void after() {
        if (runnable != null) {
          runnable.run();
        }
      }
    });
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  public void queueTypeAheadDone(final ActionCallback done) {
    queue(new AfterModelUpdate(ID.TYPE_AHEAD_FINISHED) {
      @Override
      protected void after() {
        done.setDone();
      }
    });
  }

  private abstract class AfterModelUpdate extends Update {
    private AfterModelUpdate(ID id) {
      super(id.name(), id.getPriority());
    }

    @Override
    public void run() {
      if (myModelUpdating.get()) {
        queue(this);
      } else {
        after();
      }
    }
    protected void after() {}
  }

  public static enum ID {
    MODEL(0),
    UI(1),
    REVALIDATE(2),
    SELECT(3),
    SCROLL_TO_VISIBLE(4),
    SHOW_HINT(4),
    REQUEST_FOCUS(4),
    NAVIGATE_INSIDE(4),
    TYPE_AHEAD_FINISHED(5);

    private final int myPriority;

    ID(int priority) {
      myPriority = priority;
    }

    public int getPriority() {
      return myPriority;
    }
  }
}

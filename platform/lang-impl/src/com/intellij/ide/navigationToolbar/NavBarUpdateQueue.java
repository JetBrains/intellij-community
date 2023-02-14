// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public class NavBarUpdateQueue extends MergingUpdateQueue {
  private final AtomicBoolean myModelUpdating = new AtomicBoolean(Boolean.FALSE);
  private final Alarm myUserActivityAlarm = new Alarm(this);
  private Runnable myRunWhenListRebuilt;
  private final Runnable myUserActivityAlarmRunnable = () -> processUserActivity();

  private final NavBarPanel myPanel;

  public NavBarUpdateQueue(NavBarPanel panel) {
    super("NavBar", Registry.intValue("navBar.updateMergeTime"), true, panel, panel);
    myPanel = panel;
    setTrackUiActivity(true);
    IdeEventQueue.getInstance().addActivityListener(() -> restartRebuild(), panel);
  }

  private void requestModelUpdate(@Nullable DataContext context, @Nullable Object object, boolean requeue) {
    if (myModelUpdating.getAndSet(true) && !requeue) return;

    cancelAllUpdates();

    queue(new AfterModelUpdate(ID.MODEL) {
      @Override
      public void run() {
        if (context != null || object != null) {
          requestModelUpdateFromContextOrObject(context, object);
        }
        else {
          DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(
            dataContext -> requestModelUpdateFromContextOrObject(dataContext, null));
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
    try {
      NavBarModel model = myPanel.getModel();
      if (dataContext != null) {
        Component parent = UIUtil.findUltimateParent(PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext));
        if (parent == null) return;
        Project project = parent instanceof IdeFrame ? ((IdeFrame)parent).getProject() : null;
        if (myPanel.getProject() != project || myPanel.isNodePopupActive()) {
          requestModelUpdate(null, myPanel.getContextObject(), true);
          return;
        }
        Window window = SwingUtilities.getWindowAncestor(myPanel);
        if (window != null && !window.isFocused()) {
          model.updateModelAsync(DataManager.getInstance().getDataContext(myPanel), null);
        }
        else {
          model.updateModelAsync(dataContext, null);
        }
      }
      else {
        model.updateModel(object);
      }

      queueRebuildUi();
    }
    finally {
      myModelUpdating.set(false);
    }
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
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
      Window wnd = SwingUtilities.windowForComponent(myPanel);
      if (wnd == null) return;

      Component focus = null;

      if (!wnd.isActive()) {
        IdeFrame frame = ComponentUtil.getParentOfType((Class<? extends IdeFrame>)IdeFrame.class, (Component)myPanel);
        if (frame != null) {
          focus = IdeFocusManager.getInstance(project).getLastFocusedFor((Window)frame);
        }
      }
      else {
        final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (window instanceof Dialog dialog) {
          if (dialog.isModal() && !SwingUtilities.isDescendingFrom(myPanel, dialog)) {
            return;
          }
        }
      }

      if (focus != null && focus.isShowing()) {
        if (!myPanel.isFocused() && !myPanel.isNodePopupActive()) {
          requestModelUpdate(DataManager.getInstance().getDataContext(focus), null, false);
        }
      }
      else if (wnd.isActive()) {
        if (myPanel.allowNavItemsFocus() && (myPanel.isFocused() || myPanel.isNodePopupActive())) {
          return;
        }
        requestModelUpdate(null, myPanel.getContextObject(), false);
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
        SlowOperations.allowSlowOperations(() -> rebuildUi());
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

    queueAfterAll(() -> myPanel.scrollSelectionToVisible(false), ID.SCROLL_TO_VISIBLE);
  }

  private void queueRevalidate(@Nullable final Runnable after) {
    queue(new AfterModelUpdate(ID.REVALIDATE) {
      @Override
      protected void after() {
        final LightweightHint hint = myPanel.getHint();
        if (hint != null) {
          myPanel.getHintContainerShowPoint().doWhenDone((Consumer<RelativePoint>)relativePoint -> {
            Dimension size = myPanel.getPreferredSize();
            if (ExperimentalUI.isNewUI()) {
              JBInsets.addTo(size, JBUI.CurrentTheme.StatusBar.Breadcrumbs.floatingBorderInsets());
            }
            hint.setSize(size);
            hint.setLocation(relativePoint);
            if (after != null) {
              after.run();
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

  public enum ID {
    MODEL(0),
    UI(1),
    REVALIDATE(2),
    SELECT(3),
    SCROLL_TO_VISIBLE(4),
    SHOW_HINT(4),
    REQUEST_FOCUS(4),
    NAVIGATE_INSIDE(4);

    private final int myPriority;

    ID(int priority) {
      myPriority = priority;
    }

    public int getPriority() {
      return myPriority;
    }
  }
}
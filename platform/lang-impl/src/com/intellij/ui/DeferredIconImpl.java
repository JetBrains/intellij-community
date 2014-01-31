/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class DeferredIconImpl<T> implements DeferredIcon {
  private static final int MIN_AUTO_UPDATE_MILLIS = 950;
  private static final RepaintScheduler ourRepaintScheduler = new RepaintScheduler();
  @NotNull
  private volatile Icon myDelegateIcon;
  private Function<T, Icon> myEvaluator;
  private volatile boolean myIsScheduled = false;
  private T myParam;
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_16;
  private final boolean myNeedReadAction;
  private boolean myDone;
  private final boolean myAutoUpdatable;
  private long myLastCalcTime = 0L;
  private long myLastTimeSpent = 0L;

  private final IconListener<T> myEvalListener;
  private static final TransferToEDTQueue<Runnable> ourLaterInvocator = TransferToEDTQueue.createRunnableMerger("Deferred icon later invocator", 200);

  public DeferredIconImpl(Icon baseIcon, T param, @NotNull Function<T, Icon> evaluator, @NotNull IconListener<T> listener, boolean autoUpdatable) {
    this(baseIcon, param, true, evaluator, listener, autoUpdatable);
  }

  public DeferredIconImpl(Icon baseIcon, T param, final boolean needReadAction, @NotNull Function<T, Icon> evaluator) {
    this(baseIcon, param, needReadAction, evaluator, null, false);
  }

  private DeferredIconImpl(Icon baseIcon, T param, boolean needReadAction, final Function<T, Icon> evaluator, IconListener<T> listener, boolean autoUpdatable) {
    myParam = param;
    myDelegateIcon = nonNull(baseIcon);
    myEvaluator = evaluator;
    myNeedReadAction = needReadAction;
    myEvalListener = listener;
    myAutoUpdatable = autoUpdatable;
  }

  @NotNull
  private static Icon nonNull(final Icon icon) {
    return icon == null ? EMPTY_ICON : icon;
  }

  @Override
  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    if (!(myDelegateIcon instanceof DeferredIconImpl && ((DeferredIconImpl)myDelegateIcon).myDelegateIcon instanceof DeferredIconImpl)) {
      myDelegateIcon.paintIcon(c, g, x, y); //SOE protection
    }

    if (isDone() || myIsScheduled || PowerSaveMode.isEnabled()) {
      return;
    }
    myIsScheduled = true;

    final Component target = getTarget(c);
    final Component paintingParent = SwingUtilities.getAncestorOfClass(PaintingParent.class, c);
    final Rectangle paintingParentRec = paintingParent == null ? null : ((PaintingParent)paintingParent).getChildRec(c);

    JobLauncher.getInstance().submitToJobThread(Job.DEFAULT_PRIORITY, new Runnable() {
      @Override
      public void run() {
        int oldWidth = myDelegateIcon.getIconWidth();
        final Icon[] evaluated = new Icon[1];
        final Runnable evalRunnable = new Runnable() {
          @Override
          public void run() {
            try {
              evaluated[0] = nonNull(myEvaluator.fun(myParam));
            }
            catch (ProcessCanceledException e) {
              evaluated[0] = EMPTY_ICON;
            }
            catch (IndexNotReadyException e) {
              evaluated[0] = EMPTY_ICON;
            }
          }
        };

        final long startTime = System.currentTimeMillis();
        if (myNeedReadAction) {
          if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(new Runnable() {
            @Override
            public void run() {
              IconDeferrerImpl.evaluateDeferred(evalRunnable);
              if (myAutoUpdatable) {
                myLastCalcTime = System.currentTimeMillis();
                myLastTimeSpent = myLastCalcTime - startTime;
              }
            }
          })) {
            myIsScheduled = false;
            return;
          }
        }
        else {
          IconDeferrerImpl.evaluateDeferred(evalRunnable);
          if (myAutoUpdatable) {
            myLastCalcTime = System.currentTimeMillis();
            myLastTimeSpent = myLastCalcTime - startTime;
          }
        }
        final Icon result = evaluated[0];
        myDelegateIcon = result;

        final boolean shouldRevalidate =
          Registry.is("ide.tree.deferred.icon.invalidates.cache") && myDelegateIcon.getIconWidth() != oldWidth;

        ourLaterInvocator.offer(new Runnable() {
          @Override
          public void run() {
            setDone(result);

            Component actualTarget = target;
            if (actualTarget != null && SwingUtilities.getWindowAncestor(actualTarget) == null) {
              actualTarget = paintingParent;
              if (actualTarget == null || SwingUtilities.getWindowAncestor(actualTarget) == null) {
                actualTarget = null;
              }
            }

            if (actualTarget == null) return;

            if (shouldRevalidate) {
              // revalidate will not work: jtree caches size of nodes
              if (actualTarget instanceof JTree) {
                final TreeUI ui = ((JTree)actualTarget).getUI();
                if (ui instanceof BasicTreeUI) {
                  // this call is "fake" and only need to reset tree layout cache
                  ((BasicTreeUI)ui).setLeftChildIndent(UIUtil.getTreeLeftChildIndent());
                }
              }
            }

            if (c == actualTarget) {
              c.repaint(x, y, getIconWidth(), getIconHeight());
            }
            else {
              ourRepaintScheduler.pushDirtyComponent(actualTarget, paintingParentRec);
            }
          }
        });
      }
    });
  }

  private static Component getTarget(Component c) {
    final Component target;

    final Container list = SwingUtilities.getAncestorOfClass(JList.class, c);
    if (list != null) {
      target = list;
    }
    else {
      final Container tree = SwingUtilities.getAncestorOfClass(JTree.class, c);
      if (tree != null) {
        target = tree;
      }
      else {
        final Container table = SwingUtilities.getAncestorOfClass(JTable.class, c);
        if (table != null) {
          target = table;
        }
        else {
          final Container box = SwingUtilities.getAncestorOfClass(JComboBox.class, c);
          if (box != null) {
            target = box;
          }
          else {
            final Container tabLabel = SwingUtilities.getAncestorOfClass(TabLabel.class, c);
            target = tabLabel == null ? c : tabLabel;
          }
        }
      }
    }
    return target;
  }

  private void setDone(@NotNull Icon result) {
    if (myEvalListener != null) {
      myEvalListener.evalDone(myParam, result);
    }

    myDone = true;
    if (!myAutoUpdatable) {
      myEvaluator = null;
      myParam = null;
    }
  }

  @NotNull
  @Override
  public Icon evaluate() {
    Icon result;
    try {
      result = nonNull(myEvaluator.fun(myParam));
    }
    catch (ProcessCanceledException e) {
      result = EMPTY_ICON;
    }
    catch (IndexNotReadyException e) {
      result = EMPTY_ICON;
    }

    checkDoesntReferenceThis(result);

    return result;
  }

  private void checkDoesntReferenceThis(final Icon icon) {
    if (icon == this) {
      throw new IllegalStateException("Loop in icons delegation");
    }

    if (icon instanceof DeferredIconImpl) {
      checkDoesntReferenceThis(((DeferredIconImpl)icon).myDelegateIcon);
    }
    else if (icon instanceof LayeredIcon) {
      for (Icon layer : ((LayeredIcon)icon).getAllLayers()) {
        checkDoesntReferenceThis(layer);
      }
    }
    else if (icon instanceof RowIcon) {
      final RowIcon rowIcon = (RowIcon)icon;
      final int count = rowIcon.getIconCount();
      for (int i = 0; i < count; i++) {
        checkDoesntReferenceThis(rowIcon.getIcon(i));
      }
    }
  }

  @Override
  public int getIconWidth() {
    return myDelegateIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myDelegateIcon.getIconHeight();
  }

  public boolean isDone() {
    if (myAutoUpdatable && myDone && myLastCalcTime > 0 && (System.currentTimeMillis() - myLastCalcTime) > Math.max(MIN_AUTO_UPDATE_MILLIS, 10 * myLastTimeSpent)) {
      myDone = false;
      myIsScheduled = false;
    }
    return myDone;
  }

  private static class RepaintScheduler {
    private final Alarm myAlarm = new Alarm();
    private final Set<RepaintRequest> myQueue = new LinkedHashSet<RepaintRequest>();

    public void pushDirtyComponent(@NotNull Component c, final Rectangle rec) {
      ApplicationManager.getApplication().assertIsDispatchThread(); // assert myQueue accessed from EDT only
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          for (RepaintRequest each : myQueue) {
            Rectangle r = each.getRectangle();
            if (r == null) {
              each.getComponent().repaint();
            }
            else {
              each.getComponent().repaint(r.x, r.y, r.width, r.height);
            }
          }
          myQueue.clear();
        }
      }, 50);

      myQueue.add(new RepaintRequest(c, rec));
    }
  }

  private static class RepaintRequest {
    private final Component myComponent;
    private final Rectangle myRectangle;

    private RepaintRequest(@NotNull Component component, Rectangle rectangle) {
      myComponent = component;
      myRectangle = rectangle;
    }

    @NotNull
    public Component getComponent() {
      return myComponent;
    }

    public Rectangle getRectangle() {
      return myRectangle;
    }
  }

  public interface IconListener<T> {
    void evalDone(T key, @NotNull Icon result);
  }
}

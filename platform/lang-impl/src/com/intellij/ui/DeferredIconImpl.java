/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.concurrency.JobUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class DeferredIconImpl<T> implements DeferredIcon {
  private static final RepaintScheduler ourRepaintScheduler = new RepaintScheduler();
  private volatile Icon myDelegateIcon;
  private Function<T, Icon> myEvaluator;
  private volatile boolean myIsScheduled = false;
  private T myParam;
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_16;
  private boolean myNeedReadAction;
  private boolean myDone;

  private IconDisposer<T> myDisposer;

  public DeferredIconImpl(Icon baseIcon, T param, Function<T, Icon> evaluator) {
    this(baseIcon, param, true, evaluator);
  }

  public DeferredIconImpl(Icon baseIcon, T param, final boolean needReadAction, Function<T, Icon> evaluator) {
    myParam = param;
    myDelegateIcon = nonNull(baseIcon);
    myEvaluator = evaluator;
    myNeedReadAction = needReadAction;
  }

  private static Icon nonNull(final Icon icon) {
    return icon != null ? icon : EMPTY_ICON;
  }

  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    myDelegateIcon.paintIcon(c, g, x, y);

    if (!myIsScheduled && !isDone()) {
      myIsScheduled = true;

      final Ref<Component> target = new Ref<Component>(null);
      final Ref<Component> paintingParent = new Ref<Component>(null);
      final Ref<Rectangle> paintingParentRec = new Ref<Rectangle>(null);

      final Container list = SwingUtilities.getAncestorOfClass(JList.class, c);
      if (list != null) {
        target.set(list);
      }
      else {
        final Container tree = SwingUtilities.getAncestorOfClass(JTree.class, c);
        if (tree != null) {
          target.set(tree);
        }
        else {
          final Container table = SwingUtilities.getAncestorOfClass(JTable.class, c);
          if (table != null) {
            target.set(table);
          }
          else {
            final Container box = SwingUtilities.getAncestorOfClass(JComboBox.class, c);
            if (box != null) {
              target.set(box);
            }
            else {
              target.set(c);
            }
          }
        }
      }

      Container pp = SwingUtilities.getAncestorOfClass(PaintingParent.class, c);
      paintingParent.set(pp);
      if (paintingParent.get() != null) {
        paintingParentRec.set(((PaintingParent)pp).getChildRec(c));
      }

      JobUtil.submitToJobThread(new Runnable() {
        public void run() {
          int oldWidth = myDelegateIcon.getIconWidth();
          myDelegateIcon = evaluate();

          final boolean shouldRevalidate = Registry.is("ide.tree.deferredicon.invalidates.cache") && myDelegateIcon.getIconWidth() != oldWidth;

          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              setDone();

              Component actualTarget = target.get();
              if (SwingUtilities.getWindowAncestor(actualTarget) == null) {
                actualTarget = paintingParent.get();
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
                Rectangle rec = null;
                if (paintingParentRec.get() != null) {
                  rec = paintingParentRec.get();
                }

                ourRepaintScheduler.pushDirtyComponent(actualTarget, rec);
              }
            }
          });
        }
      }, Job.DEFAULT_PRIORITY);
    }
  }

  private void setDone() {
    if (myDisposer != null) {
      myDisposer.dispose(myParam);
    }

    myDone = true;
    myEvaluator = null;
    myParam = null;
  }

  public Icon evaluate() {
    final Icon[] evaluated = new Icon[1];
    final Runnable runnable = new Runnable() {
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
    if (myNeedReadAction) {
      IconDeferrerImpl.evaluateDeferredInReadAction(runnable);
    }
    else {
      IconDeferrerImpl.evaluateDeferred(runnable);
    }

    checkDoesntReferenceThis(evaluated[0]);

    return evaluated[0];
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

  public int getIconWidth() {
    return myDelegateIcon.getIconWidth();
  }

  public int getIconHeight() {
    return myDelegateIcon.getIconHeight();
  }

  public boolean isDone() {
    return myDone;
  }

  private static class RepaintScheduler {
    private final Alarm myAlarm = new Alarm();
    private final Set<RepaintRequest> myQueue = new LinkedHashSet<RepaintRequest>();

    public void pushDirtyComponent(final Component c, final Rectangle rec) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(new Runnable() {
        public void run() {
          for (RepaintRequest each : myQueue) {
            Rectangle r = each.getRectangle();
            if (r != null) {
              each.getComponent().repaint(r.x, r.y, r.width, r.height);
            } else {
              each.getComponent().repaint();
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

    private RepaintRequest(Component component, Rectangle rectangle) {
      myComponent = component;
      myRectangle = rectangle;
    }

    public Component getComponent() {
      return myComponent;
    }

    public Rectangle getRectangle() {
      return myRectangle;
    }
  }


  public DeferredIconImpl setDisposer(IconDisposer<T> disposer) {
    myDisposer = disposer;
    return this;
  }

  public interface IconDisposer<T> {

    void dispose(T key);

  }

}

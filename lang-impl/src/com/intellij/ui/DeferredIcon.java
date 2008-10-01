/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.util.Function;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.*;

public class DeferredIcon<T> implements Icon {
  private volatile Icon myDelegateIcon;
  private final Function<T, Icon> myEvaluator;
  private volatile boolean myIsScheduled = false;
  private final T myParam;
  private Component myLastTarget = null;

  public DeferredIcon(Icon baseIcon, T param, Function<T, Icon> evaluator) {
    myParam = param;
    myDelegateIcon = nonNull(baseIcon);
    myEvaluator = evaluator;
  }

  private static Icon nonNull(final Icon icon) {
    return icon != null ? icon : new EmptyIcon(16, 16);
  }

  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    myDelegateIcon.paintIcon(c, g, x, y);

    if (!myIsScheduled) {
      myIsScheduled = true;

      final Component target;

      final Container list = SwingUtilities.getAncestorOfClass(JList.class, c);
      if (list != null) {
        target = list;
      }
      else {
        target = c;
      }

      myLastTarget = target;

      final Job<Object> job = JobScheduler.getInstance().createJob("Evaluating deferred icon", Job.DEFAULT_PRIORITY);
      job.addTask(new Runnable() {
        public void run() {
          ((IconDeferrerImpl)IconDeferrer.getInstance()).evaluateDeferred(new Runnable() {
            public void run() {
              myDelegateIcon = nonNull(myEvaluator.fun(myParam));
            }
          });

          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (c == target) {
                c.repaint(x, y, getIconWidth(), getIconHeight());
              }
              else {
                target.repaint();
              }
            }
          });
        }
      });

      job.schedule();
    }
  }

  public int getIconWidth() {
    return myDelegateIcon.getIconWidth();
  }

  public int getIconHeight() {
    return myDelegateIcon.getIconHeight();
  }

  public void invalidate() {
    myIsScheduled = false;
    if (myLastTarget != null) {
      myLastTarget.repaint();
    }
  }
}
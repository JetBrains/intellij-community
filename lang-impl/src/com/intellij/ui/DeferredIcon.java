/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Function;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.*;

public class DeferredIcon<T> implements Icon {
  private final Icon myBaseIcon;
  private volatile Icon myEvaluated;
  private final Function<T, Icon> myEvaluator;
  private boolean myIsScheduled = false;
  private final T myParam;

  public DeferredIcon(Icon baseIcon, T param, Function<T, Icon> evaluator) {
    myParam = param;
    myBaseIcon = baseIcon != null ? baseIcon : new EmptyIcon(16, 16);
    myEvaluator = evaluator;
    myEvaluated = null;
  }

  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    if (myEvaluated != null) {
      myEvaluated.paintIcon(c, g, x, y);
    }
    else {
      myBaseIcon.paintIcon(c, g, x, y);

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

        final Job<Object> job = JobScheduler.getInstance().createJob("Evaluating deferred icon", Job.DEFAULT_PRIORITY);
        job.addTask(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                myEvaluated = myEvaluator.fun(myParam);
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
  }

  public int getIconWidth() {
    return myBaseIcon.getIconWidth();
  }

  public int getIconHeight() {
    return myBaseIcon.getIconHeight();
  }
}
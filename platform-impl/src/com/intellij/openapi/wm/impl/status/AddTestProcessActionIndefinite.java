package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessActionIndefinite extends AnAction {
  public AddTestProcessActionIndefinite() {
    super("Add Test Process");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);

    new Task.Backgroundable(project, "Test", true) {
      public void run(final ProgressIndicator indicator) {
        try {
          Thread.currentThread().sleep(6000);

          countTo(900, new AddTestProcessActionIndefinite.Count() {
            public void onCount(int each) throws InterruptedException {
              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
              }
              Thread.currentThread().sleep(10);
              indicator.checkCanceled();
              indicator.setText2("bla bla bla");
            }
          });
          indicator.stop();
        }
        catch (Exception e1) {
          indicator.stop();
          return;
        }
      }
    }.queue();
  }

  private void countTo(int top, AddTestProcessActionIndefinite.Count count) throws Exception {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private static interface Count {
    void onCount(int each) throws InterruptedException;
  }
}

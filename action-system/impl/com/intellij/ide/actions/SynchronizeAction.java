
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nullable;

import java.util.Timer;
import java.util.TimerTask;

public class SynchronizeAction extends AnAction {
  private static final long PROGRESS_REPAINT_INTERVAL = 100L;

  public void actionPerformed(AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final VirtualFileManager manager = VirtualFileManager.getInstance();
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    final Project project = (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
    //This is yet another hack with modality states
    if (ModalityState.current() == ModalityState.NON_MODAL) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final @Nullable ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
          if (pi != null) {
            pi.setText(IdeBundle.message("progress.synchronizing.files"));
            pi.setIndeterminate(true);
          }
          final Semaphore refreshSemaphore = new Semaphore();
          refreshSemaphore.down();
          application.runReadAction(new Runnable() {
            public void run() {
              manager.refresh(true, new Runnable() {
                public void run() {
                  refreshSemaphore.up();
                }
              });
            }
          });
          final Timer updateTimer = new Timer(true);
          updateTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
              if (pi != null) {
                pi.setFraction(1.0);
              }
            }
          }, 0L, PROGRESS_REPAINT_INTERVAL);

          try {
            refreshSemaphore.waitFor();
          }
          finally {
            updateTimer.cancel();
          }
        }
      }, "", false, project);
    } else {
      application.runWriteAction(new Runnable() {
        public void run() {
          manager.refresh(false);
        }
      });
    }
  }
}

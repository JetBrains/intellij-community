package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;

/**
 * @author yole
 */
public class InvalidateCachesAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    FSRecords.invalidateCaches();
    final Application app = ApplicationManager.getApplication();
    if (app.isRestartCapable()) {
      int rc = Messages.showYesNoDialog(e.getData(PlatformDataKeys.PROJECT),
                                        "The caches have been invalidated and will be rebuilt on the next startup. Would you like to restart " +
                                        ApplicationNamesInfo.getInstance().getFullProductName() + " now?",
                                        "Invalidate Caches", Messages.getInformationIcon());
      if (rc == 0) {
        app.restart();
      }
    }
    else {
      Messages.showMessageDialog(e.getData(PlatformDataKeys.PROJECT),
                                 "The caches have been invalidated and will be rebuilt on the next startup",
                                 "Invalidate Caches", Messages.getInformationIcon());
    }
  }
}

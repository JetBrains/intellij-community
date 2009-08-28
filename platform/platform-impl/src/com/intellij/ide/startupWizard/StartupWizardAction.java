package com.intellij.ide.startupWizard;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

/**
 * @author yole
 */
public class StartupWizardAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final StartupWizard startupWizard = new StartupWizard(project, ApplicationInfoImpl.getShadowInstance().getPluginChooserPages());
    final String title = ApplicationNamesInfo.getInstance().getFullProductName() + " Plugin Configuration Wizard";
    startupWizard.setTitle(title);
    startupWizard.show();
    if (startupWizard.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      Messages.showInfoMessage(project, "To apply the changes, please restart " + ApplicationNamesInfo.getInstance().getFullProductName(),
                               title);
    }
  }
}

package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;

import java.io.File;

/**
 * @author irengrig
 *         Date: 5/27/11
 *         Time: 12:57 PM
 */
public class ProjectDirCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    if (new File(directory, ".idea").exists()) {
      int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.open.project.dir.prompt", directory.getPath()),
                                        VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
      if (rc == 0) {
        ProjectUtil.openProject(directory.getPath(), project, false);
      }
      return true;
    }
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}

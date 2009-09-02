package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ide.impl.NewProjectUtil;

import java.io.File;

/**
 * @author yole
 */
public class NewProjectCheckoutListener implements CheckoutListener {
  public boolean processCheckedOutDirectory(final Project project, final File directory) {
    int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.create.project.prompt", directory.getAbsolutePath()),
                                      VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
    if (rc == 0) {
      NewProjectUtil.createNewProject(project, directory.getAbsolutePath());
      return true;
    }
    return false;
  }
}

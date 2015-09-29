/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Proposes to create a new project from the checked out sources.
 */
public class NewProjectCheckoutListener implements CheckoutListener {
  private static final Logger LOG = Logger.getInstance(NewProjectCheckoutListener.class);

  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
    LOG.assertTrue(file != null, "Can't find " + directory);
    int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.create.project.prompt",
                                                                 ProjectCheckoutListener.getProductNameWithArticle(),
                                                                 directory.getAbsolutePath()),
                                      VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
    if (rc == Messages.YES) {
      AddModuleWizard wizard = createImportWizard(file);
      if (wizard == null) return false;
      if (wizard.showAndGet()) {
        ImportModuleAction.createFromWizard(null, wizard);
      }
      return true;
    }
    return false;
  }

  @Nullable
  protected AddModuleWizard createImportWizard(@NotNull VirtualFile file) {
    return ImportModuleAction.createImportWizard(null, null, file, ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions());
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}

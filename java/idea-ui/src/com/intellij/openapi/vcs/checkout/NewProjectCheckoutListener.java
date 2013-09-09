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
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class NewProjectCheckoutListener implements VcsAwareCheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory, VcsKey vcsKey) {
    int rc = Messages.showYesNoDialog(project, VcsBundle.message("checkout.create.project.prompt",
                                                                 ProjectCheckoutListener.getProductNameWithArticle(),
                                                                 directory.getAbsolutePath()),
                                      VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
    if (rc == 0) {
      final ProjectManager pm = ProjectManager.getInstance();
      final Project[] projects = pm.getOpenProjects();
      final Set<VirtualFile> files = projectsLocationSet(projects);
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(directory);
      AddModuleWizard wizard = createImportWizard(file);
      if (wizard == null) return false;
      if (wizard.showAndGet()) {
        ImportModuleAction.createFromWizard(null, wizard);
      }
      final Project[] projectsAfter = pm.getOpenProjects();

      for (Project project1 : projectsAfter) {
        if (project1.getBaseDir() != null && ! files.contains(project1.getBaseDir())) {
          final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project1);
          vcsManager.setDirectoryMappings(Collections.singletonList(new VcsDirectoryMapping("", vcsKey.getName())));
          break;
        }
      }
      return true;
    }
    return false;
  }

  @Nullable
  protected AddModuleWizard createImportWizard(VirtualFile file) {
    return ImportModuleAction.createImportWizard(null, null, file, ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions());
  }

  private static Set<VirtualFile> projectsLocationSet(Project[] projects) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    for (Project project1 : projects) {
      if (project1.getBaseDir() != null) {
        files.add(project1.getBaseDir());
      }
    }
    return files;
  }
}

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

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.Icons;

public class CreateDirectoryOrPackageAction extends AnAction implements DumbAware {
  public CreateDirectoryOrPackageAction() {
    super(IdeBundle.message("action.create.new.directory.or.package"), IdeBundle.message("action.create.new.directory.or.package"), null);
  }

  public void actionPerformed(AnActionEvent e) {
    IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    Project project = e.getData(PlatformDataKeys.PROJECT);

    PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    boolean isDirectory = !PsiDirectoryFactory.getInstance(project).isPackage(directory);

    CreateDirectoryOrPackageHandler validator = new CreateDirectoryOrPackageHandler(project, directory, isDirectory,
                                                                                   isDirectory ? "\\/" : ".");
    Messages.showInputDialog(project, isDirectory
                                      ? IdeBundle.message("prompt.enter.new.directory.name")
                                      : IdeBundle.message("prompt.enter.new.package.name"),
                                      isDirectory ? IdeBundle.message("title.new.directory") : IdeBundle.message("title.new.package"),
                                      Messages.getQuestionIcon(), "", validator);

    final PsiElement result = validator.getCreatedElement();
    if (result != null && view != null) {
      view.selectElement(result);
    }
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setEnabled(true);

    boolean isPackage = false;
    final PsiDirectoryFactory factory = PsiDirectoryFactory.getInstance(project);
    for (PsiDirectory directory : directories) {
      if (factory.isPackage(directory)) {
        isPackage = true;
        break;
      }
    }

    if (isPackage) {
      presentation.setText(IdeBundle.message("action.package"));
      presentation.setIcon(Icons.PACKAGE_ICON);
    }
    else {
      presentation.setText(IdeBundle.message("action.directory"));
      presentation.setIcon(Icons.DIRECTORY_OPEN_ICON);
    }
  }

}

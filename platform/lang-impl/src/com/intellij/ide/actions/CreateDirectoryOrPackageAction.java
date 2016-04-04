/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.PlatformIcons;

public class CreateDirectoryOrPackageAction extends AnAction implements DumbAware {
  public CreateDirectoryOrPackageAction() {
    super(IdeBundle.message("action.create.new.directory.or.package"), IdeBundle.message("action.create.new.directory.or.package"), null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);

    if (view == null || project == null) {
      return;
    }
    PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    final boolean isDirectory = !PsiDirectoryFactory.getInstance(project).isPackage(directory);

    final CreateDirectoryOrPackageHandler validator = new CreateDirectoryOrPackageHandler(project, directory, isDirectory,
                                                                                   isDirectory ? "\\/" : ".");
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
      public void run() {
        Messages.showInputDialog(project, isDirectory
                                          ? IdeBundle.message("prompt.enter.new.directory.name")
                                          : IdeBundle.message("prompt.enter.new.package.name"),
                                          isDirectory ? IdeBundle.message("title.new.directory") : IdeBundle.message("title.new.package"),
                                          Messages.getQuestionIcon(), "", validator);
      }
    });

    final PsiElement result = validator.getCreatedElement();
    if (result != null) {
      view.selectElement(result);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    Project project = event.getData(CommonDataKeys.PROJECT);
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
      presentation.setIcon(PlatformIcons.PACKAGE_ICON);
    }
    else {
      presentation.setText(IdeBundle.message("action.directory"));
      presentation.setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
    }
  }

}

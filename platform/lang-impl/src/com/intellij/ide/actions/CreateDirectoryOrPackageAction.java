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

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

public class CreateDirectoryOrPackageAction extends AnAction implements DumbAware {
  public CreateDirectoryOrPackageAction() {
    super(IdeBundle.message("action.create.new.directory.or.package"), IdeBundle.message("action.create.new.directory.or.package"), null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    boolean isDirectory = !PsiDirectoryFactory.getInstance(project).isPackage(directory);

    MyInputValidator validator = new MyInputValidator(project, directory, isDirectory);
    Messages.showInputDialog(project, isDirectory
                                      ? IdeBundle.message("prompt.enter.new.directory.name")
                                      : IdeBundle.message("prompt.enter.new.package.name"),
                                      isDirectory ? IdeBundle.message("title.new.directory") : IdeBundle.message("title.new.package"),
                                      Messages.getQuestionIcon(), "", validator);

    if (validator.myCreatedElement == null) return;

    view.selectElement(validator.myCreatedElement);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
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

  protected class MyInputValidator implements InputValidatorEx {
    private final Project myProject;
    private final PsiDirectory myDirectory;
    private final boolean myIsDirectory;
    private PsiElement myCreatedElement = null;

    public MyInputValidator(Project project, PsiDirectory directory, boolean isDirectory) {
      myProject = project;
      myDirectory = directory;
      myIsDirectory = isDirectory;
    }

    public boolean checkInput(String inputString) {
      return true;
    }

    public String getErrorText(String inputString) {
      if (FileTypeManager.getInstance().isFileIgnored(inputString)) {
        return "Trying to create a " + (myIsDirectory ? "directory" : "package") + " with ignored name, result will not be visible";
      }
      if (!myIsDirectory && inputString.length() > 0 && !PsiDirectoryFactory.getInstance(myProject).isValidPackageName(inputString)) {
        return "Not a valid package name";
      }
      return null;
    }

    public boolean canClose(String inputString) {
      final String subDirName = inputString;

      if (subDirName.length() == 0) {
        Messages.showMessageDialog(myProject, IdeBundle.message("error.name.should.be.specified"), CommonBundle.getErrorTitle(),
                                   Messages.getErrorIcon());
        return false;
      }

      final boolean multiCreation = myIsDirectory
                                    ? subDirName.indexOf('/') != -1 || subDirName.indexOf('\\') != -1
                                    : subDirName.indexOf('.') != -1;

      if (!multiCreation) {
        try {
          myDirectory.checkCreateSubdirectory(subDirName);
        }
        catch (IncorrectOperationException ex) {
          Messages.showMessageDialog(myProject, CreateElementActionBase.filterMessage(ex.getMessage()), CommonBundle.getErrorTitle(),
                                     Messages.getErrorIcon());
          return false;
        }
      }

      Runnable command = new Runnable() {
        public void run() {
          final Runnable run = new Runnable() {
            public void run() {
              LocalHistoryAction action = LocalHistoryAction.NULL;
              try {
                String actionName;
                String dirPath = myDirectory.getVirtualFile().getPresentableUrl();
                actionName = IdeBundle.message("progress.creating.directory", dirPath, File.separator, subDirName);
                action = LocalHistory.getInstance().startAction(actionName);

                final PsiDirectory createdDir;
                if (myIsDirectory) {
                  createdDir = DirectoryUtil.createSubdirectories(subDirName, myDirectory, "\\/");
                }
                else {
                  createdDir = DirectoryUtil.createSubdirectories(subDirName, myDirectory, ".");
                }


                myCreatedElement = createdDir;

              }
              catch (final IncorrectOperationException ex) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    Messages.showMessageDialog(myProject, CreateElementActionBase.filterMessage(ex.getMessage()),
                                               CommonBundle.getErrorTitle(), Messages.getErrorIcon());
                  }
                });
              }
              finally {
                action.finish();
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(run);
        }
      };
      CommandProcessor.getInstance().executeCommand(myProject, command, myIsDirectory
                                                                        ? IdeBundle.message("command.create.directory")
                                                                        : IdeBundle.message("command.create.package"), null);

      return myCreatedElement != null;
    }
  }
}

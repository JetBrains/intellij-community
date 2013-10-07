/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class CreateDirectoryOrPackageHandler implements InputValidatorEx {
  @Nullable private final Project myProject;
  @NotNull private final PsiDirectory myDirectory;
  private final boolean myIsDirectory;
  @Nullable private PsiFileSystemItem myCreatedElement = null;
  @NotNull private final String myDelimiters;
  @Nullable private final Component myDialogParent;

  public CreateDirectoryOrPackageHandler(@Nullable Project project,
                                         @NotNull PsiDirectory directory,
                                         boolean isDirectory,
                                         @NotNull final String delimiters) {
    this(project, directory, isDirectory, delimiters, null);
  }

  public CreateDirectoryOrPackageHandler(@Nullable Project project,
                                         @NotNull PsiDirectory directory,
                                         boolean isDirectory,
                                         @NotNull final String delimiters,
                                         @Nullable Component dialogParent) {
    myProject = project;
    myDirectory = directory;
    myIsDirectory = isDirectory;
    myDelimiters = delimiters;
    myDialogParent = dialogParent;
  }

  @Override
  public boolean checkInput(String inputString) {
    return true;
  }

  @Override
  public String getErrorText(String inputString) {
    if (FileTypeManager.getInstance().isFileIgnored(inputString)) {
      return "Trying to create a " + (myIsDirectory ? "directory" : "package") + " with ignored name, result will not be visible";
    }
    if (!myIsDirectory && inputString.length() > 0 && !PsiDirectoryFactory.getInstance(myProject).isValidPackageName(inputString)) {
      return "Not a valid package name, it would be impossible to create a class inside";
    }
    return null;
  }

  @Override
  public boolean canClose(String inputString) {
    final String subDirName = inputString;

    if (subDirName.length() == 0) {
      showErrorDialog(IdeBundle.message("error.name.should.be.specified"));
      return false;
    }

    final boolean multiCreation = StringUtil.containsAnyChar(subDirName, myDelimiters);
    if (!multiCreation) {
      try {
        myDirectory.checkCreateSubdirectory(subDirName);
      }
      catch (IncorrectOperationException ex) {
        showErrorDialog(CreateElementActionBase.filterMessage(ex.getMessage()));
        return false;
      }
    }
    
    boolean createFile = false;
    if (StringUtil.countChars(subDirName, '.') == 1) {
      FileType fileType = findFileTypeBoundToName(subDirName);
      if (fileType != null) {
        String message = "The name you entered looks like a file name. Do you want to create a file named " + subDirName + " instead?";
        int ec = Messages.showYesNoDialog(myProject, message,
                                           "File Name Detected", "Yes, create file",
                                           "No, create " + (myIsDirectory ? "directory" : "packages"),
                                           fileType.getIcon());
        if (ec == Messages.OK) {
          createFile = true;
        }
      }
    }

    doCreateElement(subDirName, createFile);

    return myCreatedElement != null;
  }

  @Nullable
  public static FileType findFileTypeBoundToName(String name) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    return fileType instanceof UnknownFileType ? null : fileType;
  }

  private void doCreateElement(final String subDirName, final boolean createFile) {
    Runnable command = new Runnable() {
      @Override
      public void run() {
        final Runnable run = new Runnable() {
          @Override
          public void run() {
            LocalHistoryAction action = LocalHistoryAction.NULL;
            try {
              String actionName;
              String dirPath = myDirectory.getVirtualFile().getPresentableUrl();
              actionName = IdeBundle.message("progress.creating.directory", dirPath, File.separator, subDirName);
              action = LocalHistory.getInstance().startAction(actionName);

              if (createFile) {
                myCreatedElement = myDirectory.createFile(subDirName);
              } else {
                createDirectories(subDirName);
              }
            }
            catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  showErrorDialog(CreateElementActionBase.filterMessage(ex.getMessage()));
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
    CommandProcessor.getInstance().executeCommand(myProject, command, createFile ? IdeBundle.message("command.create.file") 
                                                                                 : myIsDirectory
                                                                      ? IdeBundle.message("command.create.directory")
                                                                      : IdeBundle.message("command.create.package"), null);
  }

  private void showErrorDialog(String message) {
    String title = CommonBundle.getErrorTitle();
    Icon icon = Messages.getErrorIcon();
    if (myDialogParent != null) {
      Messages.showMessageDialog(myDialogParent, message, title, icon);
    }
    else {
      Messages.showMessageDialog(myProject, message, title, icon);
    }
  }

  protected void createDirectories(String subDirName) {
    myCreatedElement = DirectoryUtil.createSubdirectories(subDirName, myDirectory, myDelimiters);
  }

  @Nullable
  public PsiFileSystemItem getCreatedElement() {
    return myCreatedElement;
  }
}

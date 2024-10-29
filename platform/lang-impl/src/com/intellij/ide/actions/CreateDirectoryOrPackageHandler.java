// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.StringTokenizer;

public class CreateDirectoryOrPackageHandler implements InputValidatorEx {
  private final @Nullable Project myProject;
  private final @NotNull PsiDirectory myDirectory;
  private final boolean myIsDirectory;
  private @Nullable PsiFileSystemItem myCreatedElement = null;
  private final @NotNull String myDelimiters;
  private final @Nullable Component myDialogParent;
  private @NlsContexts.DetailedDescription String myErrorText;
  private @NlsContexts.DetailedDescription String myWarningText;

  public CreateDirectoryOrPackageHandler(@Nullable Project project,
                                         @NotNull PsiDirectory directory,
                                         boolean isDirectory,
                                         final @NotNull String delimiters) {
    this(project, directory, isDirectory, delimiters, null);
  }

  public CreateDirectoryOrPackageHandler(@Nullable Project project,
                                         @NotNull PsiDirectory directory,
                                         boolean isDirectory,
                                         final @NotNull String delimiters,
                                         @Nullable Component dialogParent) {
    myProject = project;
    myDirectory = directory;
    myIsDirectory = isDirectory;
    myDelimiters = delimiters;
    myDialogParent = dialogParent;
  }

  @Override
  public boolean checkInput(String inputString) {
    myErrorText = checkForErrors(inputString);
    if (myErrorText == null) {
      myWarningText = checkForWarnings(inputString);
    }
    else {
      myWarningText = null;
    }
    return myErrorText == null;
  }

  private @Nullable @NlsContexts.DetailedDescription String checkForErrors(String inputString) {
    final StringTokenizer tokenizer = new StringTokenizer(inputString, myDelimiters);
    VirtualFile vFile = myDirectory.getVirtualFile();
    boolean firstToken = true;
    while (tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();
      if (!tokenizer.hasMoreTokens() && (token.equals(".") || token.equals(".."))) {
        return IdeBundle.message("error.invalid.directory.name", token);
      }
      if (vFile != null) {
        if (firstToken && "~".equals(token)) {
          final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
          if (userHomeDir == null) {
            return IdeBundle.message("error.user.home.directory.not.found");
          }
          vFile = userHomeDir;
        }
        else if ("..".equals(token)) {
          final VirtualFile parent = vFile.getParent();
          if (parent == null) {
            return IdeBundle.message("error.invalid.directory", vFile.getPresentableUrl() + File.separatorChar + "..");
          }
          vFile = parent;
        }
        else if (!".".equals(token)){
          final VirtualFile child = vFile.findChild(token);
          if (child != null) {
            if (!child.isDirectory()) {
              return IdeBundle.message("error.file.with.name.already.exists", token);
            }
            else if (!tokenizer.hasMoreTokens()) {
              return IdeBundle.message("error.directory.with.name.already.exists", token);
            }
          }
          vFile = child;
        }
      }
      firstToken = false;
    }
    return null;
  }

  private @Nullable @NlsContexts.DetailedDescription String checkForWarnings(String inputString) {
    final StringTokenizer tokenizer = new StringTokenizer(inputString, myDelimiters);
    while (tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();
      if (FileTypeManager.getInstance().isFileIgnored(token)) {
        return myIsDirectory ? IdeBundle.message("warning.create.directory.with.ignored.name", token)
                                    : IdeBundle.message("warning.create.package.with.ignored.name", token);
      }
      if (!myIsDirectory && !token.isEmpty() && myProject != null && !PsiDirectoryFactory.getInstance(myProject).isValidPackageName(token)) {
        return IdeBundle.message("error.invalid.java.package.name");
      }
    }
    if (myIsDirectory && inputString.contains(".") && hasNoPathDelimiters(inputString)) {
      return IdeBundle.message("warning.create.directory.with.dot");
    }
    return null;
  }

  private boolean hasNoPathDelimiters(String string) {
    for (int i = 0; i < myDelimiters.length(); i++) {
      char delimiter = myDelimiters.charAt(i);
      if (string.contains(String.valueOf(delimiter))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String getErrorText(String inputString) {
    return myErrorText;
  }

  @Override
  public @Nullable String getWarningText(String inputString) {
    return myWarningText;
  }

  @Override
  public boolean canClose(final String subDirName) {
    if (subDirName.isEmpty()) {
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

    final Boolean createFile = suggestCreatingFileInstead(subDirName);
    if (createFile == null) {
      return false;
    }

    doCreateElement(subDirName, createFile);

    return myCreatedElement != null;
  }

  private @Nullable Boolean suggestCreatingFileInstead(String subDirName) {
    Boolean createFile = false;
    if (StringUtil.countChars(subDirName, '.') == 1 && Registry.is("ide.suggest.file.when.creating.filename.like.directory")) {
      if (findFileTypeBoundToName(subDirName) != null) {
        String message = LangBundle.message("dialog.message.name.you.entered", subDirName);
        int ec = Messages.showYesNoCancelDialog(myProject, message,
                                                LangBundle.message("dialog.title.file.name.detected"),
                                                LangBundle.message("button.yes.create.file"),
                                                LangBundle.message("button.no.create", myIsDirectory ?
                                                                                       LangBundle.message("button.no.create.directory") :
                                                                                       LangBundle.message("button.no.create.package")),
                                                CommonBundle.getCancelButtonText(),
                                                Messages.getQuestionIcon());
        if (ec == Messages.CANCEL) {
          createFile = null;
        }
        if (ec == Messages.YES) {
          createFile = true;
        }
      }
    }
    return createFile;
  }

  public static @Nullable FileType findFileTypeBoundToName(String name) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    return fileType instanceof UnknownFileType ? null : fileType;
  }

  private void doCreateElement(final String subDirName, final boolean createFile) {
    Runnable command = () -> {
      final Runnable run = () -> {
        String dirPath = myDirectory.getVirtualFile().getPresentableUrl();
        String actionName = IdeBundle.message("progress.creating.directory", dirPath, File.separator, subDirName);
        LocalHistoryAction action = LocalHistory.getInstance().startAction(actionName);
        try {
          if (createFile) {
            CreateFileAction.MkDirs mkdirs = new CreateFileAction.MkDirs(subDirName, myDirectory);
            myCreatedElement = mkdirs.directory.createFile(mkdirs.newName);
          } else {
            createDirectories(subDirName);
          }
        }
        catch (final IncorrectOperationException ex) {
          ApplicationManager.getApplication().invokeLater(() -> showErrorDialog(CreateElementActionBase.filterMessage(ex.getMessage())));
        }
        finally {
          action.finish();
        }
      };
      ApplicationManager.getApplication().runWriteAction(run);
    };
    CommandProcessor.getInstance().executeCommand(myProject, command, createFile ? IdeBundle.message("command.create.file") 
                                                                                 : myIsDirectory
                                                                      ? IdeBundle.message("command.create.directory")
                                                                      : IdeBundle.message("command.create.package"), null);
  }

  private void showErrorDialog(@NlsContexts.DialogMessage String message) {
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

  public @Nullable PsiFileSystemItem getCreatedElement() {
    return myCreatedElement;
  }
}

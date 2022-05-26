// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CreateFileAction extends CreateElementActionBase implements DumbAware {

  public CreateFileAction() {
    super(ActionsBundle.messagePointer("action.NewFile.text"), IdeBundle.messagePointer("action.create.new.file.description"), AllIcons.FileTypes.Text);
  }

  public CreateFileAction(@NlsActions.ActionText String text,
                          @NlsActions.ActionDescription String description,
                          final Icon icon) {
    super(text, description, icon);
  }

  public CreateFileAction(@NotNull Supplier<String> dynamicText, @NotNull Supplier<String> dynamicDescription, final Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  @Override
  public boolean isDumbAware() {
    return CreateFileAction.class.equals(getClass());
  }

  @Override
  protected PsiElement @NotNull [] invokeDialog(final @NotNull Project project, @NotNull PsiDirectory directory) {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  protected void invokeDialog(@NotNull Project project, @NotNull PsiDirectory directory, @NotNull Consumer<? super PsiElement[]> elementsConsumer) {
    MyInputValidator validator = new MyValidator(project, directory);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      try {
        elementsConsumer.accept(validator.create("test"));
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    else {
      if (Experiments.getInstance().isFeatureEnabled("show.create.new.element.in.popup")) {
        createLightWeightPopup(validator, elementsConsumer).showCenteredInCurrentWindow(project);
      }
      else {
        Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.file.name"),
                                 IdeBundle.message("title.new.file"), null, null, validator);
        elementsConsumer.accept(validator.getCreatedElements());
      }
    }
  }

  private @NotNull JBPopup createLightWeightPopup(@NotNull MyInputValidator validator,
                                                  @NotNull Consumer<? super PsiElement[]> consumer) {
    NewItemSimplePopupPanel contentPanel = new NewItemSimplePopupPanel();
    JTextField nameField = contentPanel.getTextField();
    JBPopup popup = NewItemPopupUtil.createNewItemPopup(IdeBundle.message("title.new.file"), contentPanel, nameField);
    contentPanel.setApplyAction(event -> {
      String name = nameField.getText();
      if (validator.checkInput(name) && validator.canClose(name)) {
        popup.closeOk(event);
        consumer.accept(validator.getCreatedElements());
      }
      else {
        String errorMessage = validator instanceof InputValidatorEx
                              ? ((InputValidatorEx)validator).getErrorText(name)
                              : LangBundle.message("incorrect.name");
        contentPanel.setError(errorMessage);
      }
    });

    return popup;
  }

  @Override
  protected PsiElement @NotNull [] create(@NotNull String newName, @NotNull PsiDirectory directory) throws Exception {
    MkDirs mkdirs = new MkDirs(newName, directory);
    return new PsiElement[]{WriteAction.compute(() -> mkdirs.directory.createFile(getFileName(mkdirs.newName)))};
  }

  @NotNull
  public static PsiDirectory findOrCreateSubdirectory(@NotNull PsiDirectory parent, @NotNull String subdirName) {
    final PsiDirectory sub = parent.findSubdirectory(subdirName);
    return sub == null ? WriteAction.compute(() -> parent.createSubdirectory(subdirName)) : sub;
  }

  public static class MkDirs {
    @NotNull
    public final String newName;
    @NotNull
    public final PsiDirectory directory;

    public MkDirs(@NotNull String newName, @NotNull PsiDirectory directory) {
      if (SystemInfo.isWindows) {
        newName = newName.replace('\\', '/');
      }
      if (newName.contains("/")) {
        final List<String> subDirs = StringUtil.split(newName, "/");
        newName = subDirs.remove(subDirs.size() - 1);
        boolean firstToken = true;
        for (String dir : subDirs) {
          if (firstToken && "~".equals(dir)) {
            final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
            if (userHomeDir == null) throw new IncorrectOperationException("User home directory not found");
            final PsiDirectory directory1 = directory.getManager().findDirectory(userHomeDir);
            if (directory1 == null) throw new IncorrectOperationException("User home directory not found");
            directory = directory1;
          }
          else if ("..".equals(dir)) {
            final PsiDirectory parentDirectory = directory.getParentDirectory();
            if (parentDirectory == null) throw new IncorrectOperationException("Not a valid directory");
            directory = parentDirectory;
          }
          else if (!".".equals(dir)){
            directory = findOrCreateSubdirectory(directory, dir);
          }
          firstToken = false;
        }
      }

      this.newName = newName;
      this.directory = directory;
    }
  }

  @Override
  protected @NotNull String getActionName(@NotNull PsiDirectory directory, @NotNull String newName) {
    return IdeBundle.message("progress.creating.file", directory.getVirtualFile().getPresentableUrl(), File.separator, newName);
  }

  @Override
  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.file");
  }

  protected String getFileName(String newName) {
    if (getDefaultExtension() == null || !FileUtilRt.getExtension(newName).isEmpty()) {
      return newName;
    }
    return newName + "." + getDefaultExtension();
  }

  @Nullable
  protected String getDefaultExtension() {
    return null;
  }

  protected class MyValidator extends MyInputValidator implements InputValidatorEx {
    private @NlsContexts.DetailedDescription String myErrorText;

    public MyValidator(Project project, PsiDirectory directory){
      super(project, directory);
    }

    @Override
    public boolean checkInput(String inputString) {
      final StringTokenizer tokenizer = new StringTokenizer(inputString, "\\/");
      VirtualFile vFile = getDirectory().getVirtualFile();
      boolean firstToken = true;
      while (tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();
        if ((token.equals(".") || token.equals("..")) && !tokenizer.hasMoreTokens()) {
          myErrorText = IdeBundle.message("error.invalid.file.name", token);
          return false;
        }
        if (vFile != null) {
          if (firstToken && "~".equals(token)) {
            final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
            if (userHomeDir == null) {
              myErrorText = IdeBundle.message("error.user.home.directory.not.found");
              return false;
            }
            vFile = userHomeDir;
          }
          else if ("..".equals(token)) {
            final VirtualFile parent = vFile.getParent();
            if (parent == null) {
              myErrorText = IdeBundle.message("error.invalid.directory", vFile.getPresentableUrl() + File.separatorChar + "..");
              return false;
            }
            vFile = parent;
          }
          else if (!".".equals(token)) {
            final VirtualFile child = vFile.findChild(token);
            if (child != null) {
              if (!child.isDirectory()) {
                myErrorText = IdeBundle.message("error.file.with.name.already.exists", token);
                return false;
              }
              else if (!tokenizer.hasMoreTokens()) {
                myErrorText = IdeBundle.message("error.directory.with.name.already.exists", token);
                return false;
              }
            }
            vFile = child;
          }
        }
        if (FileTypeManager.getInstance().isFileIgnored(getFileName(token))) {
          myErrorText = IdeBundle.message("warning.create.directory.with.ignored.name", token);
          return true;
        }
        firstToken = false;
      }
      myErrorText = null;
      return true;
    }

    @Override
    public String getErrorText(String inputString) {
      return myErrorText;
    }

    @Override
    public boolean canClose(final String inputString) {
      if (inputString.isEmpty()) {
        return super.canClose(inputString);
      }

      return super.canClose(getFileName(inputString));
    }
  }
}

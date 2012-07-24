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

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class CreateFileAction extends CreateElementActionBase implements DumbAware {

  public CreateFileAction() {
    super(IdeBundle.message("action.create.new.file"), IdeBundle.message("action.create.new.file"), AllIcons.FileTypes.Text);
  }

  public CreateFileAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  @Override
  public boolean isDumbAware() {
    return CreateFileAction.class.equals(getClass());
  }

  @NotNull
  protected PsiElement[] invokeDialog(final Project project, PsiDirectory directory) {
    MyInputValidator validator = new MyValidator(project, directory);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      try {
        return validator.create("test");
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    else {
      Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.file.name"),
                               IdeBundle.message("title.new.file"), Messages.getQuestionIcon(), null, validator);
      return validator.getCreatedElements();
    }
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    if (SystemInfo.isWindows) {
      newName = newName.replace('\\', '/');
    }
    if (newName.contains("/")) {
      final List<String> subDirs = StringUtil.split(newName, "/");
      newName = subDirs.remove(subDirs.size() - 1);
      for (String dir : subDirs) {
        final PsiDirectory sub = directory.findSubdirectory(dir);
        directory = sub == null ? directory.createSubdirectory(dir) : sub;
      }
    }
    return new PsiElement[]{directory.createFile(getFileName(newName))};
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return IdeBundle.message("progress.creating.file", directory.getVirtualFile().getPresentableUrl(), File.separator, newName);
  }

  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.file");
  }

  protected String getCommandName() {
    return IdeBundle.message("command.create.file");
  }

  protected String getFileName(String newName) {
    if (getDefaultExtension() == null || FileUtil.getExtension(newName).length() > 0) {
      return newName;
    }
    return newName + "." + getDefaultExtension();
  }

  @Nullable
  protected String getDefaultExtension() {
    return null;
  }

  protected class MyValidator extends MyInputValidator implements InputValidatorEx {
    private String myErrorText;

    public MyValidator(Project project, PsiDirectory directory){
      super(project, directory);
    }

    @Override
    public boolean checkInput(String inputString) {
      if (FileTypeManager.getInstance().isFileIgnored(getFileName(inputString))) {
        myErrorText = "This filename is ignored (Settings | File Types | Ignore files and folders)";
        return false;
      }
      myErrorText = null;
      return true;
    }

    @Override
    public String getErrorText(String inputString) {
      return myErrorText;
    }

    public boolean canClose(String inputString) {
      if (inputString.length() == 0) {
        return super.canClose(inputString);
      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(getFileName(inputString));
      return type != null && super.canClose(getFileName(inputString));
    }
  }
}

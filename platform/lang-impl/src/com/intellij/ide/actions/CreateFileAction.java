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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

public class CreateFileAction extends CreateElementActionBase implements DumbAware {

  public CreateFileAction() {
    super(IdeBundle.message("action.create.new.file"), IdeBundle.message("action.create.new.file"), IconLoader.getIcon("/fileTypes/text.png"));
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
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.file.name"),
                             IdeBundle.message("title.new.file"), Messages.getQuestionIcon(), null, validator);
    return validator.getCreatedElements();
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    directory.checkCreateFile(newName);
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return new PsiElement[]{directory.createFile(newName)};
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

  protected class MyValidator extends MyInputValidator {
    public boolean checkInput(String inputString) {
      return true;
    }

    public boolean canClose(String inputString) {
      if (inputString.length() == 0) {
        return super.canClose(inputString);
      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(inputString);
      return type != null && super.canClose(inputString);
    }

    public MyValidator(Project project, PsiDirectory directory){
      super(project, directory);
    }
  }
}

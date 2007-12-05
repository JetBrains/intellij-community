/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * The standard "New Class" action.
 *
 * @since 5.1
 */
public class CreateClassAction extends CreateInPackageActionBase {
  public CreateClassAction() {
    super(IdeBundle.message("action.create.new.class"),
          IdeBundle.message("action.create.new.class"), Icons.CLASS_ICON);
  }

  @NotNull
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.class.name"),
                             IdeBundle.message("title.new.class"), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();
  }

  protected String getCommandName() {
    return IdeBundle.message("command.create.class");
  }


  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.class");
  }


  protected String getActionName(PsiDirectory directory, String newName) {
    return IdeBundle.message("progress.creating.class", JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName(), newName);
  }

  protected void doCheckCreate(final PsiDirectory dir, final String className) throws IncorrectOperationException {
    JavaDirectoryService.getInstance().checkCreateClass(dir, className);
  }


  protected PsiClass doCreate(final PsiDirectory dir, final String className) throws IncorrectOperationException {
    return JavaDirectoryService.getInstance().createClass(dir, className);
  }
}

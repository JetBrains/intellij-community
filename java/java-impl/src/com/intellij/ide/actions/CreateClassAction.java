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
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The standard "New Class" action.
 *
 * @since 5.1
 */
public class CreateClassAction extends JavaCreateTemplateInPackageAction<PsiClass> implements DumbAware {
  public CreateClassAction() {
    super("", IdeBundle.message("action.create.new.class.description"), PlatformIcons.CLASS_ICON, true);
  }

  @Override
  protected void buildDialog(final Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(IdeBundle.message("action.create.new.class"))
      .addKind("Class", PlatformIcons.CLASS_ICON, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME)
      .addKind("Interface", PlatformIcons.INTERFACE_ICON, JavaTemplateUtil.INTERNAL_INTERFACE_TEMPLATE_NAME);
    LanguageLevel level = PsiUtil.getLanguageLevel(directory);
    if (level.isAtLeast(LanguageLevel.JDK_1_5)) {
      builder.addKind("Enum", PlatformIcons.ENUM_ICON, JavaTemplateUtil.INTERNAL_ENUM_TEMPLATE_NAME);
      builder.addKind("Annotation", PlatformIcons.ANNOTATION_TYPE_ICON, JavaTemplateUtil.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME);
    }
    
    for (FileTemplate template : FileTemplateManager.getInstance(project).getAllTemplates()) {
      final JavaCreateFromTemplateHandler handler = new JavaCreateFromTemplateHandler();
      if (handler.handlesTemplate(template) && JavaCreateFromTemplateHandler.canCreate(directory)) {
        builder.addKind(template.getName(), JavaFileType.INSTANCE.getIcon(), template.getName());
      }
    }
    
    builder.setValidator(new InputValidatorEx() {
      @Override
      public String getErrorText(String inputString) {
        if (inputString.length() > 0 && !PsiNameHelper.getInstance(project).isQualifiedName(inputString)) {
          return "This is not a valid Java qualified name";
        }

        if (level.isAtLeast(LanguageLevel.JDK_X) && PsiKeyword.VAR.equals(StringUtil.getShortName(inputString))) {
          return "var cannot be used for type declarations";
        }
        return null;
      }

      @Override
      public boolean checkInput(String inputString) {
        return true;
      }

      @Override
      public boolean canClose(String inputString) {
        return !StringUtil.isEmptyOrSpaces(inputString) && getErrorText(inputString) == null;
      }
    });
  }

  @Override
  protected String removeExtension(String templateName, String className) {
    return StringUtil.trimEnd(className, ".java");
  }

  @Override
  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.class");
  }


  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return IdeBundle.message("progress.creating.class", StringUtil.getQualifiedName(JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName(), newName));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  protected final PsiClass doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    return JavaDirectoryService.getInstance().createClass(dir, className, templateName, true);
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull PsiClass createdElement) {
    return createdElement.getLBrace();
  }

  @Override
  protected void postProcess(PsiClass createdElement, String templateName, Map<String, String> customProperties) {
    super.postProcess(createdElement, templateName, customProperties);

    moveCaretAfterNameIdentifier(createdElement);
  }
}

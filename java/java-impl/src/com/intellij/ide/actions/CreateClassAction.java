// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.fileTemplates.*;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The standard "New Class" action for Java.
 */
public class CreateClassAction extends JavaCreateTemplateInPackageAction<PsiClass> implements DumbAware {
  public CreateClassAction() {
    super("", JavaBundle.message("action.create.new.class.description"),
          IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class), true);
  }

  @Override
  protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory,
                             @NotNull CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(JavaBundle.message("action.create.new.class"))
      .addKind(JavaPsiBundle.message("node.class.tooltip"), IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class), JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME)
      .addKind(JavaPsiBundle.message("node.interface.tooltip"), PlatformIcons.INTERFACE_ICON, JavaTemplateUtil.INTERNAL_INTERFACE_TEMPLATE_NAME);

    LanguageLevel level = PsiUtil.getLanguageLevel(directory);
    if (JavaFeature.RECORDS.isSufficient(level)) {
      builder.addKind(JavaPsiBundle.message("node.record.tooltip"), PlatformIcons.RECORD_ICON, JavaTemplateUtil.INTERNAL_RECORD_TEMPLATE_NAME);
    }
    if (JavaFeature.ENUMS.isSufficient(level)) {
      builder.addKind(JavaPsiBundle.message("node.enum.tooltip"), PlatformIcons.ENUM_ICON, JavaTemplateUtil.INTERNAL_ENUM_TEMPLATE_NAME);
    }
    if (JavaFeature.ANNOTATIONS.isSufficient(level)) {
      builder.addKind(JavaPsiBundle.message("node.annotation.tooltip"), PlatformIcons.ANNOTATION_TYPE_ICON, JavaTemplateUtil.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME);
    }

    builder.addKind(JavaPsiBundle.message("node.exception.tooltip"), PlatformIcons.EXCEPTION_CLASS_ICON,
                    JavaTemplateUtil.INTERNAL_EXCEPTION_TYPE_TEMPLATE_NAME);

    if (JavaFeature.IMPLICIT_CLASSES.isSufficient(level)) {
      builder.addKind(JavaPsiBundle.message("node.simple.source.file.tooltip"),
                      IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.JavaFileType),
                      JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE);
    }

    PsiDirectory[] dirs = {directory};
    for (FileTemplate template : FileTemplateManager.getInstance(project).getAllTemplates()) {
      @NotNull CreateFromTemplateHandler handler = FileTemplateUtil.findHandler(template);
      if (handler instanceof JavaCreateFromTemplateHandler && 
          handler.handlesTemplate(template) && 
          handler.canCreate(dirs)) {
        builder.addKind(template.getName(), JavaFileType.INSTANCE.getIcon(), template.getName());
      }
    }

    builder.setValidator(new InputValidatorEx() {
      @Override
      public String getErrorText(String inputString) {
        if (!inputString.isEmpty() && !PsiNameHelper.getInstance(project).isQualifiedName(inputString)) {
          return JavaErrorBundle.message("create.class.action.this.not.valid.java.qualified.name");
        }
        String shortName = StringUtil.getShortName(inputString);
        if (PsiTypesUtil.isRestrictedIdentifier(shortName, level)) {
          return JavaErrorBundle.message("restricted.identifier", shortName);
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
  protected @NotNull String getErrorTitle() {
    return JavaBundle.message("title.cannot.create.class");
  }


  @Override
  protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
    return JavaBundle.message("progress.creating.class", StringUtil.getQualifiedName(psiPackage == null ? "" : psiPackage.getQualifiedName(), newName));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected final PsiClass doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    return JavaDirectoryService.getInstance().createClass(dir, className, templateName, true);
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull PsiClass createdElement) {
    if (createdElement.isRecord()) {
      PsiRecordHeader header = createdElement.getRecordHeader();
      if (header != null) {
        return header.getLastChild();
      }
    }
    return createdElement.getLBrace();
  }

  @SuppressWarnings("RedundantMethodOverride")
  @Override
  protected void postProcess(@NotNull PsiClass createdElement, String templateName, Map<String, String> customProperties) {
    // This override is necessary for plugin compatibility
    super.postProcess(createdElement, templateName, customProperties);
  }
}

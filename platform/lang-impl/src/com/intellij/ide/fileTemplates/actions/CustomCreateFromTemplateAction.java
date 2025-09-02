// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

import static com.intellij.ide.fileTemplates.actions.CreateFromTemplateManager.startLiveTemplate;
import static com.intellij.util.ObjectUtils.notNull;

@ApiStatus.Experimental
public abstract class CustomCreateFromTemplateAction extends CreateFileFromTemplateAction {
  @NotNull private final String myTemplateName;

  protected CustomCreateFromTemplateAction(@NotNull String name) {
    myTemplateName = name;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  protected void buildDialog(@NotNull Project project,
                             @NotNull PsiDirectory directory,
                             @NotNull CreateFileFromTemplateDialog.Builder builder) {
    FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(project);
    FileTemplate template = fileTemplateManager.getInternalTemplate(myTemplateName);

    builder
      .setTitle(UIBundle.message("create.new.file.from.template.dialog.title", myTemplateName))
      .addKind(myTemplateName, FileTemplateUtil.getIcon(template), template.getName());

    customizeBuilder(builder, directory);
  }

  protected void customizeBuilder(@NotNull CreateFileFromTemplateDialog.Builder builder, @NotNull PsiDirectory directory) { }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
    return templateName;
  }

  @Override
  protected void postProcess(@NotNull PsiFile createdElement,
                             @NotNull DataContext dataContext,
                             String templateName,
                             Map<String, String> customProperties) {
    super.postProcess(createdElement, dataContext, templateName, customProperties);

    var selectedTemplate = FileTemplateManager.getInstance(createdElement.getProject())
      .getInternalTemplate(templateName);

    if (selectedTemplate.isLiveTemplateEnabled()) {
      Map<String, String> defaultValues = getLiveTemplateDefaults(dataContext, createdElement);
      startLiveTemplate(createdElement, notNull(defaultValues, Collections.emptyMap()));
    }
  }

  protected @Nullable Map<String, String> getLiveTemplateDefaults(@NotNull DataContext dataContext, @NotNull PsiFile file) {
    return null;
  }
}

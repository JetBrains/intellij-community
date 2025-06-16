// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * Consider using {@link CustomCreateFromTemplateAction} instead for subclasses.
 */
public abstract class CreateFromTemplateActionBase extends AnAction {
  public CreateFromTemplateActionBase() {
    super();
  }

  public CreateFromTemplateActionBase(@NlsActions.ActionText String title,
                                      @NlsActions.ActionDescription String description,
                                      Icon icon) {
    super(title, description, icon);
  }

  public CreateFromTemplateActionBase(@NotNull Supplier<String> dynamicTitle, @NotNull Supplier<String> dynamicDescription, Icon icon) {
    super(dynamicTitle, dynamicDescription, icon);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) return;
    PsiDirectory dir = getTargetDirectory(dataContext, view);
    if (dir == null) return;
    Project project = dir.getProject();

    FileTemplate selectedTemplate = getTemplate(project, dir);
    if (selectedTemplate != null) {
      AnAction action = getReplacedAction(selectedTemplate);
      if (action != null) {
        action.actionPerformed(e);
      }
      else {
        FileTemplateManager.getInstance(project).addRecentName(selectedTemplate.getName());
        AttributesDefaults defaults = getAttributesDefaults(dataContext);
        Properties properties = defaults != null ? defaults.getDefaultProperties() : null;
        CreateFromTemplateDialog dialog = new CreateFromTemplateDialog(project, dir, selectedTemplate, defaults, properties);
        PsiElement createdElement = dialog.create();
        if (createdElement != null) {
          elementCreated(dialog, createdElement);
          view.selectElement(createdElement);
          if (selectedTemplate.isLiveTemplateEnabled() && createdElement instanceof PsiFile) {
            Map<String, String> defaultValues = getLiveTemplateDefaults(dataContext, ((PsiFile)createdElement));
            startLiveTemplate((PsiFile)createdElement, notNull(defaultValues, Collections.emptyMap()));
          }
        }
      }
    }
  }

  /**
   * @deprecated Use {@link CreateFromTemplateManager#startLiveTemplate(PsiFile)} instead
   */
  @Deprecated
  public static void startLiveTemplate(@NotNull PsiFile file) {
    CreateFromTemplateManager.startLiveTemplate(file, Collections.emptyMap());
  }

  /**
   * @deprecated Use {@link CreateFromTemplateManager#startLiveTemplate(PsiFile, Map)} instead
   */
  @Deprecated
  public static void startLiveTemplate(@NotNull PsiFile file, @NotNull Map<String, String> defaultValues) {
    CreateFromTemplateManager.startLiveTemplate(file, defaultValues);
  }

  protected @Nullable PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
    return DirectoryChooserUtil.getOrChooseDirectory(view);
  }

  protected abstract FileTemplate getTemplate(Project project, PsiDirectory dir);

  protected @Nullable AnAction getReplacedAction(FileTemplate selectedTemplate) {
    return null;
  }

  protected @Nullable AttributesDefaults getAttributesDefaults(DataContext dataContext) {
    return null;
  }

  protected void elementCreated(CreateFromTemplateDialog dialog, PsiElement createdElement) { }

  protected @Nullable Map<String, String> getLiveTemplateDefaults(DataContext dataContext, @NotNull PsiFile file) {
    return null;
  }
}

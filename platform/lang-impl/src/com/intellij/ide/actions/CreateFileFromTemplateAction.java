// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateFileFromTemplateAction extends CreateFromTemplateAction<PsiFile> {

  protected CreateFileFromTemplateAction() {
  }

  public CreateFileFromTemplateAction(@NlsActions.ActionText String text,
                                      @NlsActions.ActionDescription String description,
                                      @Nullable Icon icon) {
    super(text, description, icon);
  }

  public CreateFileFromTemplateAction(@NotNull Supplier<String> dynamicText,
                                      @NotNull Supplier<String> dynamicDescription,
                                      @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  protected PsiFile createFileFromTemplate(final String name, final FileTemplate template, final PsiDirectory dir) {
    return createFileFromTemplate(name, template, dir, getDefaultTemplateProperty(), true);
  }

  public static @Nullable PsiFile createFileFromTemplate(@Nullable String name,
                                                         @NotNull FileTemplate template,
                                                         @NotNull PsiDirectory dir,
                                                         @Nullable String defaultTemplateProperty,
                                                         boolean openFile) {
    return createFileFromTemplate(name, template, dir, defaultTemplateProperty, openFile, Collections.emptyMap());
  }

  public static @Nullable PsiFile createFileFromTemplate(@Nullable String name,
                                                         @NotNull FileTemplate template,
                                                         @NotNull PsiDirectory dir,
                                                         @Nullable String defaultTemplateProperty,
                                                         boolean openFile,
                                                         @NotNull Map<String, String> liveTemplateDefaultValues) {
    return createFileFromTemplate(name, template, dir, defaultTemplateProperty, openFile, liveTemplateDefaultValues, Collections.emptyMap());
  }

  public static @Nullable PsiFile createFileFromTemplate(@Nullable String name,
                                                         @NotNull FileTemplate template,
                                                         @NotNull PsiDirectory dir,
                                                         @Nullable String defaultTemplateProperty,
                                                         boolean openFile,
                                                         @NotNull Map<String, String> liveTemplateDefaultValues,
                                                         @NotNull Map<String, String> extraTemplateProperties) {
    if (name != null) {
      CreateFileAction.MkDirs mkdirs = new CreateFileAction.MkDirs(name, dir);
      name = mkdirs.newName;
      dir = mkdirs.directory;
    }

    Project project = dir.getProject();
    try {
      Properties templateProperties = FileTemplateManager.getInstance(dir.getProject()).getDefaultProperties();
      templateProperties.putAll(extraTemplateProperties);

      PsiFile psiFile = FileTemplateUtil.createFromTemplate(template, name, templateProperties, dir)
        .getContainingFile();
      SmartPsiElementPointer<PsiFile> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiFile);

      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        FileTypeUsageCounterCollector.logCreated(project, virtualFile, template);

        if (openFile) {
          if (template.isLiveTemplateEnabled()) {
            CreateFromTemplateActionBase.startLiveTemplate(psiFile, liveTemplateDefaultValues);
          }
          else {
            FileEditorManager.getInstance(project).openFile(virtualFile, true);
          }
        }
        if (defaultTemplateProperty != null) {
          PropertiesComponent.getInstance(project).setValue(defaultTemplateProperty, template.getName());
        }
        return pointer.getElement();
      }
    }
    catch (ParseException e) {
      throw new IncorrectOperationException("Error parsing Velocity template: " + e.getMessage(), (Throwable)e);
    }
    catch (IncorrectOperationException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }

    return null;
  }

  @Override
  protected PsiFile createFile(String name, String templateName, PsiDirectory dir) {
    final FileTemplate template = FileTemplateManager.getInstance(dir.getProject()).getInternalTemplate(templateName);
    return createFileFromTemplate(name, template, dir);
  }
}

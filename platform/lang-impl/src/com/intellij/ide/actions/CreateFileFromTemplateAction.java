/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateFileFromTemplateAction extends CreateFromTemplateAction<PsiFile> {

  public CreateFileFromTemplateAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected PsiFile createFileFromTemplate(String name, FileTemplate template, PsiDirectory dir) {
    return createFileFromTemplate(name, template, dir, getDefaultTemplateProperty());
  }

  @SuppressWarnings("DialogTitleCapitalization")
  @Nullable
  public static PsiFile createFileFromTemplate(@Nullable String name,
                                               @NotNull FileTemplate template,
                                               @NotNull PsiDirectory dir,
                                               @Nullable String defaultTemplateProperty) {
    CreateFileAction.MkDirs mkdirs = new CreateFileAction.MkDirs(name, dir);
    name = mkdirs.newName;
    dir = mkdirs.directory;
    PsiElement element;
    Project project = dir.getProject();
    try {
      element = FileTemplateUtil
        .createFromTemplate(template, name, FileTemplateManager.getInstance().getDefaultProperties(project), dir);
      final PsiFile psiFile = element.getContainingFile();

      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        FileEditorManager.getInstance(project).openFile(virtualFile, true);
        if (defaultTemplateProperty != null) {
          PropertiesComponent.getInstance(project).setValue(defaultTemplateProperty, template.getName());
        }
        return psiFile;
      }
    }
    catch (ParseException e) {
      Messages.showErrorDialog(project, "Error parsing Velocity template: " + e.getMessage(), "Create File from Template");
      return null;
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
    final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(templateName);
    return createFileFromTemplate(name, template, dir);
  }
}

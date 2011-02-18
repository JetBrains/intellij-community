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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.runtime.parser.ParseException;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateFileFromTemplateAction extends CreateFromTemplateAction<PsiFile> {

  public CreateFileFromTemplateAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected PsiFile createFileFromTemplate(String name, FileTemplate template, PsiDirectory dir) {

    PsiElement element;
    try {
      element = FileTemplateUtil
        .createFromTemplate(template, name, FileTemplateManager.getInstance().getDefaultProperties(), dir);
      final PsiFile psiFile = element.getContainingFile();

      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        FileEditorManager.getInstance(dir.getProject()).openFile(virtualFile, true);
        String property = getDefaultTemplateProperty();
        if (property != null) {
          PropertiesComponent.getInstance(dir.getProject()).setValue(property, template.getName());
        }
        return psiFile;
      }
    }
    catch (ParseException e) {
      Messages.showErrorDialog(dir.getProject(), "Error parsing Velocity template: " + e.getMessage(), "Create File from Template");
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

  @Override
  protected void checkBeforeCreate(String name, String templateName, PsiDirectory dir) {
    final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(templateName);
    if (template != null) {
    String extension = FileUtil.getExtension(name);
      if (!extension.equals(template.getExtension())) {
        name = name + '.' + extension;
      }
    }
    super.checkBeforeCreate(name, templateName, dir);
  }
}

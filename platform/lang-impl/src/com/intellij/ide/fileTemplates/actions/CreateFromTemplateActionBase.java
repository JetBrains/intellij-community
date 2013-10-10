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

package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class CreateFromTemplateActionBase extends AnAction {

  public CreateFromTemplateActionBase(final String title, final String description, final Icon icon) {
    super (title,description,icon);
  }

  @Override
  public final void actionPerformed(AnActionEvent e){
    DataContext dataContext = e.getDataContext();

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    PsiDirectory dir = getTargetDirectory(dataContext, view);
    if (dir == null) return;

    FileTemplate selectedTemplate = getTemplate(project, dir);
    if(selectedTemplate != null){
      AnAction action = getReplacedAction(selectedTemplate);
      if (action != null) {
        action.actionPerformed(e);
      }
      else {
        FileTemplateManager.getInstance().addRecentName(selectedTemplate.getName());
        final AttributesDefaults defaults = getAttributesDefaults(dataContext);
        final CreateFromTemplateDialog dialog = new CreateFromTemplateDialog(project, dir, selectedTemplate, defaults,
                                                                             defaults != null ? defaults.getDefaultProperties() : null);
        PsiElement createdElement = dialog.create();
        if (createdElement != null) {
          elementCreated(dialog, createdElement);
          view.selectElement(createdElement);
        }
      }
    }
  }

  @Nullable
  protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
    return DirectoryChooserUtil.getOrChooseDirectory(view);
  }

  @Nullable
  protected abstract AnAction getReplacedAction(final FileTemplate selectedTemplate);

  protected abstract FileTemplate getTemplate(final Project project, final PsiDirectory dir);

  @Nullable
  public AttributesDefaults getAttributesDefaults(DataContext dataContext) {
    return null;
  }

  protected void elementCreated(CreateFromTemplateDialog dialog, PsiElement createdElement) {
  }
}

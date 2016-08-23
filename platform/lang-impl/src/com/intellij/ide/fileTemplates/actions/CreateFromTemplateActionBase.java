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

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

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
    PsiDirectory dir = getTargetDirectory(dataContext, view);
    if (dir == null) return;
    Project project = dir.getProject();

    FileTemplate selectedTemplate = getTemplate(project, dir);
    if (selectedTemplate != null){
      AnAction action = getReplacedAction(selectedTemplate);
      if (action != null) {
        action.actionPerformed(e);
      }
      else {
        FileTemplateManager.getInstance(project).addRecentName(selectedTemplate.getName());
        final AttributesDefaults defaults = getAttributesDefaults(dataContext);
        final CreateFromTemplateDialog dialog = new CreateFromTemplateDialog(project, dir, selectedTemplate, defaults,
                                                                             defaults != null ? defaults.getDefaultProperties() : null);
        PsiElement createdElement = dialog.create();
        if (createdElement != null) {
          elementCreated(dialog, createdElement);
          view.selectElement(createdElement);
          if (selectedTemplate.isLiveTemplateEnabled() && createdElement instanceof PsiFile) {
            startLiveTemplate((PsiFile)createdElement);
          }
        }
      }
    }
  }

  public static void startLiveTemplate(PsiFile file) {
    Project project = file.getProject();
    final Editor editor = EditorHelper.openInEditor(file);
    if (editor == null) return;

    final TemplateImpl template = new TemplateImpl("", file.getText(), "");
    template.setInline(true);
    int count = template.getSegmentsCount();
    if (count == 0) return;

    Set<String> variables = new HashSet<>();
    for (int i = 0; i < count; i++) {
      variables.add(template.getSegmentName(i));
    }
    variables.removeAll(TemplateImpl.INTERNAL_VARS_SET);
    for (String variable : variables) {
      template.addVariable(variable, null, "\"" + variable + "\"", true);
    }
    WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().setText(template.getTemplateText()));
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }

  @Nullable
  protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
    return DirectoryChooserUtil.getOrChooseDirectory(view);
  }

  @Nullable
  protected AnAction getReplacedAction(final FileTemplate selectedTemplate) {
    return null;
  }

  protected abstract FileTemplate getTemplate(final Project project, final PsiDirectory dir);

  @Nullable
  public AttributesDefaults getAttributesDefaults(DataContext dataContext) {
    return null;
  }

  protected void elementCreated(CreateFromTemplateDialog dialog, PsiElement createdElement) {
  }
}

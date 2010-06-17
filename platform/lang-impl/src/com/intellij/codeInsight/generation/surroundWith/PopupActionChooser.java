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

package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.WrapWithCustomTemplateAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PopupActionChooser {
  private final String myTitle;
  private boolean hasEnabledSurrounders;

  public PopupActionChooser(String title) {
    myTitle = title;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file, final Surrounder[] surrounders, final PsiElement[] elements){
    final DefaultActionGroup applicable = new DefaultActionGroup();
    hasEnabledSurrounders = false;

    Set<Character> usedMnemonicsSet = new HashSet<Character>();

    int index = 0;
    for (Surrounder surrounder : surrounders) {
      if (surrounder.isApplicable(elements)) {
        char mnemonic;
        if (index < 9) {
          mnemonic = (char)('0' + index + 1);
        }
        else if (index == 9) {
          mnemonic = '0';
        }
        else {
          mnemonic = (char)('A' + index - 10);
        }
        index++;
        usedMnemonicsSet.add(Character.toUpperCase(mnemonic));
        applicable.add(new InvokeSurrounderAction(surrounder, project, editor, elements, mnemonic));
        hasEnabledSurrounders = true;
      }
    }

    List<CustomLiveTemplate> customTemplates = SurroundWithTemplateHandler.getApplicableCustomTemplates(editor, file);
    List<TemplateImpl> templates = SurroundWithTemplateHandler.getApplicableTemplates(editor, file, true);

    if (!templates.isEmpty() || !customTemplates.isEmpty()) {
      applicable.addSeparator("Live templates");
    }

    for (TemplateImpl template : templates) {
      applicable.add(new InvokeTemplateAction(template, editor, project, usedMnemonicsSet));
      hasEnabledSurrounders = true;
    }

    for (CustomLiveTemplate customTemplate : customTemplates) {
      applicable.add(new WrapWithCustomTemplateAction(customTemplate, editor, file, usedMnemonicsSet));
      hasEnabledSurrounders = true;
    }

    if (!templates.isEmpty() || !customTemplates.isEmpty()) {
      applicable.addSeparator();
      applicable.add(new ConfigureTemplatesAction());
    }

    if (hasEnabledSurrounders) {
      DataContext context = DataManager.getInstance().getDataContext(editor.getContentComponent());
      final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(myTitle,
                               applicable,
                               context,
                               JBPopupFactory.ActionSelectionAid.MNEMONICS, 
                               true);
      popup.showInBestPositionFor(editor);
    }
  }

  private static class InvokeSurrounderAction extends AnAction {
    private final Surrounder mySurrounder;
    private final Project myProject;
    private final Editor myEditor;
    private final PsiElement[] myElements;

    public InvokeSurrounderAction(Surrounder surrounder, Project project, Editor editor, PsiElement[] elements, char mnemonic) {
      super(UIUtil.MNEMONIC + String.valueOf(mnemonic) + ". " + surrounder.getTemplateDescription());
      mySurrounder = surrounder;
      myProject = project;
      myEditor = editor;
      myElements = elements;
    }


    public void actionPerformed(AnActionEvent e) {
      CommandProcessor.getInstance().executeCommand(
          myProject, new Runnable(){
          public void run(){
            final Runnable action = new Runnable(){
              public void run(){
                SurroundWithHandler.doSurround(myProject, myEditor, mySurrounder, myElements);
              }
            };
            ApplicationManager.getApplication().runWriteAction(action);
          }
        },
        null,
        null
      );
    }
  }

  public boolean isHasEnabledSurrounders() {
    return hasEnabledSurrounders;
  }

  private static class ConfigureTemplatesAction extends AnAction {
    private ConfigureTemplatesAction() {
      super("Configure Live Templates...");
    }

    public void actionPerformed(AnActionEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(e.getData(PlatformDataKeys.PROJECT), "Live Templates");
    }
  }
}

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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author mike
 */
public class SurroundWithTemplateHandler implements CodeInsightActionHandler {
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
      if (!editor.getSelectionModel().hasSelection()) return;
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    List<CustomLiveTemplate> customTemplates = getApplicableCustomTemplates(editor, file);
    ArrayList<TemplateImpl> templates = getApplicableTemplates(editor, file, true);
    if (templates.isEmpty() && customTemplates.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, CodeInsightBundle.message("templates.surround.no.defined"));
      return;
    }

    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) return;

    Set<Character> usedMnemonicsSet = new HashSet<Character>();
    DefaultActionGroup group = new DefaultActionGroup();

    for (TemplateImpl template : templates) {
      group.add(new InvokeTemplateAction(template, editor, project, usedMnemonicsSet));
    }

    for (CustomLiveTemplate customTemplate : customTemplates) {
      group.add(new WrapWithCustomTemplateAction(customTemplate, editor, file, usedMnemonicsSet));
    }

    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(CodeInsightBundle.message("templates.select.template.chooser.title"), group,
                              DataManager.getInstance().getDataContext(editor.getContentComponent()),
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, false);

    popup.showInBestPositionFor(editor);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static List<CustomLiveTemplate> getApplicableCustomTemplates(Editor editor, PsiFile file) {
    List<CustomLiveTemplate> result = new ArrayList<CustomLiveTemplate>();
    int offset = editor.getCaretModel().getOffset();
    for (CustomLiveTemplate template : CustomLiveTemplate.EP_NAME.getExtensions()) {
      if (template.supportsWrapping() && template.isApplicable(file, offset)) {
        result.add(template);
      }
    }
    return result;
  }

  public static ArrayList<TemplateImpl> getApplicableTemplates(Editor editor, PsiFile file, boolean selection) {
    int offset = editor.getCaretModel().getOffset();
    int startOffset = offset;
    if (selection && editor.getSelectionModel().hasSelection()) {
      final int selStart = editor.getSelectionModel().getSelectionStart();
      final int selEnd = editor.getSelectionModel().getSelectionEnd();
      startOffset = (offset == selStart) ? selEnd : selStart;
    }
    Set<TemplateImpl> array = new LinkedHashSet<TemplateImpl>();
    for (TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      if (!template.isDeactivated() &&
          template.isSelectionTemplate() == selection &&
          (TemplateManagerImpl.isApplicable(file, offset, template) ||
           (selection && TemplateManagerImpl.isApplicable(file, startOffset, template)))) {
        array.add(template);
      }
    }
    final ArrayList<TemplateImpl> list = new ArrayList<TemplateImpl>(array);
    Collections.sort(list, new Comparator<TemplateImpl>() {
      public int compare(TemplateImpl o1, TemplateImpl o2) {
        return o1.getKey().compareTo(o2.getKey());
      }
    });
    return list;
  }
}

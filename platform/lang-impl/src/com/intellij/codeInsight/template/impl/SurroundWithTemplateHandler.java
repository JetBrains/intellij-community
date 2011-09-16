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
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public class SurroundWithTemplateHandler implements CodeInsightActionHandler {
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    DefaultActionGroup group = createActionGroup(project, editor, file);
    if (group == null) return;

    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(CodeInsightBundle.message("templates.select.template.chooser.title"), group,
                              DataManager.getInstance().getDataContext(editor.getContentComponent()),
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, false);

    popup.showInBestPositionFor(editor);
  }

  @Nullable
  public static DefaultActionGroup createActionGroup(Project project, Editor editor, PsiFile file) {
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
      if (!editor.getSelectionModel().hasSelection()) return null;
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    List<CustomLiveTemplate> customTemplates = getApplicableCustomTemplates(editor, file);
    ArrayList<TemplateImpl> templates = getApplicableTemplates(editor, file, true);
    if (templates.isEmpty() && customTemplates.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, CodeInsightBundle.message("templates.surround.no.defined"));
      return null;
    }

    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) return null;

    Set<Character> usedMnemonicsSet = new HashSet<Character>();
    DefaultActionGroup group = new DefaultActionGroup();

    for (TemplateImpl template : templates) {
      group.add(new InvokeTemplateAction(template, editor, project, usedMnemonicsSet));
    }

    for (CustomLiveTemplate customTemplate : customTemplates) {
      group.add(new WrapWithCustomTemplateAction(customTemplate, editor, file, usedMnemonicsSet));
    }
    return group;
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static List<CustomLiveTemplate> getApplicableCustomTemplates(Editor editor, PsiFile file) {
    List<CustomLiveTemplate> result = new ArrayList<CustomLiveTemplate>();
    for (CustomLiveTemplate template : CustomLiveTemplate.EP_NAME.getExtensions()) {
      if (template.supportsWrapping() && isApplicable(template, editor, file)) {
        result.add(template);
      }
    }
    return result;
  }

  public static boolean isApplicable(CustomLiveTemplate template, Editor editor, PsiFile file) {
    return template.isApplicable(file, editor.getSelectionModel().getSelectionStart(), true);
  }

  public static ArrayList<TemplateImpl> getApplicableTemplates(Editor editor, PsiFile file, boolean selection) {

    int startOffset = editor.getCaretModel().getOffset();
    if (selection && editor.getSelectionModel().hasSelection()) {
      startOffset = editor.getSelectionModel().getSelectionStart();
    }

    file = insertDummyIdentifier(editor, file);

    ArrayList<TemplateImpl> list = new ArrayList<TemplateImpl>();
    for (TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      if (!template.isDeactivated() &&
          template.isSelectionTemplate() == selection &&
          TemplateManagerImpl.isApplicable(file, startOffset, template)) {
        list.add(template);
      }
    }
    return list;
  }

  public static PsiFile insertDummyIdentifier(final Editor editor, PsiFile file) {
    file = (PsiFile)file.copy();
    final Document document = file.getViewProvider().getDocument();
    assert document != null;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        int offset = editor.getCaretModel().getOffset();
        if (editor.getSelectionModel().hasSelection()) {
          offset = editor.getSelectionModel().getSelectionStart();
          document.deleteString(offset, editor.getSelectionModel().getSelectionEnd());
        }
        document.insertString(offset, CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
      }
    });

    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
    return file;
  }
}

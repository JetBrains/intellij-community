package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.*;

/**
 * @author mike
 */
public class SurroundWithTemplateHandler implements CodeInsightActionHandler {
  public void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
      if (!editor.getSelectionModel().hasSelection()) return;
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    int offset = editor.getCaretModel().getOffset();
    TemplateContextType contextType = TemplateManager.getInstance(project).getContextType(file, offset);
    TemplateImpl[] templates = TemplateSettings.getInstance().getTemplates();
    ArrayList<TemplateImpl> array = new ArrayList<TemplateImpl>();
    for (TemplateImpl template : templates) {
      if (template.isDeactivated()) continue;
      if (template.getTemplateContext().isEnabled(contextType) && template.isSelectionTemplate()) {
        array.add(template);
      }
    }
    if (array.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, CodeInsightBundle.message("templates.surround.no.defined"));
      return;
    }

    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) return;

    Collections.sort(array, new Comparator<TemplateImpl>() {
      public int compare(TemplateImpl o1, TemplateImpl o2) {
        return o1.getKey().compareTo(o2.getKey());
      }
    });

    Set<Character> usedMnemonicsSet = new HashSet<Character>();
    DefaultActionGroup group = new DefaultActionGroup();
    for (TemplateImpl template : array) {
      group.add(new InvokeTemplateAction(template, editor, project, usedMnemonicsSet));
    }

    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      CodeInsightBundle.message("templates.select.template.chooser.title"),
      group,
      DataManager.getInstance().getDataContext(editor.getContentComponent()),
      JBPopupFactory.ActionSelectionAid.MNEMONICS,
      false);

    popup.showInBestPositionFor(editor);
  }

  public boolean startInWriteAction() {
    return true;
  }


}

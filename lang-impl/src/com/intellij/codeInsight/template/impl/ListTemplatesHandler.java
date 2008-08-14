
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.Result;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;

public class ListTemplatesHandler implements CodeInsightActionHandler{
  public void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!file.isWritable()) return;
    EditorUtil.fillVirtualSpaceUntil(editor, editor.getCaretModel().getLogicalPosition().column, editor.getCaretModel().getLogicalPosition().line);

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    int offset = editor.getCaretModel().getOffset();
    String prefix = getPrefix(editor.getDocument(), offset);
    TemplateContextType contextType = TemplateManager.getInstance(project).getContextType(file, offset);

    TemplateImpl[] templates = TemplateSettings.getInstance().getTemplates();
    ArrayList<LookupItem> array = new ArrayList<LookupItem>();
    for (TemplateImpl template : templates) {
      if (template.isDeactivated() || template.isSelectionTemplate()) continue;
      String key = template.getKey();
      if (key.startsWith(prefix) && contextType.isEnabled(template.getTemplateContext())) {
        LookupItem item = new LookupItem(template, key);
        array.add(item);
      }
    }
    LookupItem[] items = array.toArray(new LookupItem[array.size()]);

    if (items.length == 0){
      String text = prefix.length() == 0
        ? CodeInsightBundle.message("templates.no.defined")
        : CodeInsightBundle.message("templates.no.defined.with.prefix", prefix);
      HintManager.getInstance().showErrorHint(editor, text);
      return;
    }

    final LookupImpl lookup = (LookupImpl) LookupManager.getInstance(project).createLookup(editor, items, prefix, null, null);
    lookup.addLookupListener(
      new LookupAdapter() {
        public void itemSelected(LookupEvent event) {
          new WriteCommandAction(project) {
            protected void run(Result result) throws Throwable {
              TemplateManager.getInstance(project).startTemplate(editor, '\0');
            }
          }.execute();
        }
      }
    );
    lookup.show();
  }

  public boolean startInWriteAction() {
    return true;
  }

  private String getPrefix(Document document, int offset) {
    CharSequence chars = document.getCharsSequence();
    int start = offset;
    while(true){
      if (start == 0) break;
      char c = chars.charAt(start - 1);
      if (!isInPrefix(c)) break;
      start--;
    }
    return chars.subSequence(start, offset).toString();
  }

  private boolean isInPrefix(final char c) {
    return Character.isJavaIdentifierPart(c) || c == '.';
  }
}
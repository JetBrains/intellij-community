
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
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ListTemplatesHandler implements CodeInsightActionHandler{
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    if (!file.isWritable()) return;
    EditorUtil.fillVirtualSpaceUntilCaret(editor);

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    int offset = editor.getCaretModel().getOffset();
    String prefix = getPrefix(editor.getDocument(), offset);

    List<TemplateImpl> matchingTemplates = new ArrayList<TemplateImpl>();
    for (TemplateImpl template : SurroundWithTemplateHandler.getApplicableTemplates(editor, file, false)) {
      if (template.getKey().startsWith(prefix)) {
        matchingTemplates.add(template);
      }
    }
    
    if (matchingTemplates.size() == 0) {
      String text = prefix.length() == 0
        ? CodeInsightBundle.message("templates.no.defined")
        : CodeInsightBundle.message("templates.no.defined.with.prefix", prefix);
      HintManager.getInstance().showErrorHint(editor, text);
      return;
    }

    showTemplatesLookup(project, editor, prefix, matchingTemplates);
  }

  public static void showTemplatesLookup(final Project project, final Editor editor, String prefix, List<TemplateImpl> matchingTemplates) {
    ArrayList<LookupItem> array = new ArrayList<LookupItem>();
    for (TemplateImpl template: matchingTemplates) {
      array.add(new LookupItem(template, template.getKey()));
    }
    LookupElement[] items = array.toArray(new LookupElement[array.size()]);

    final LookupImpl lookup = (LookupImpl) LookupManager.getInstance(project).createLookup(editor, items, prefix, LookupArranger.DEFAULT);
    lookup.addLookupListener(
      new LookupAdapter() {
        public void itemSelected(LookupEvent event) {
          final LookupElement lookupElement = event.getItem();
          if (lookupElement != null) {
            final TemplateImpl template = (TemplateImpl)lookupElement.getObject();
            new WriteCommandAction(project) {
              protected void run(Result result) throws Throwable {
                ((TemplateManagerImpl) TemplateManager.getInstance(project)).startTemplateWithPrefix(editor, template, null);
              }
            }.execute();
          }
        }
      }
    );
    lookup.show();
  }

  public boolean startInWriteAction() {
    return true;
  }

  private static String getPrefix(Document document, int offset) {
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

  private static boolean isInPrefix(final char c) {
    return Character.isJavaIdentifierPart(c) || c == '.';
  }
}

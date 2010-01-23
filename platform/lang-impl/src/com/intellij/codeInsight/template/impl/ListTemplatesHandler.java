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
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
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
import java.util.Map;

public class ListTemplatesHandler implements CodeInsightActionHandler {
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
    for (TemplateImpl template : matchingTemplates) {
      array.add(new LookupItem(template, template.getKey()));
    }
    LookupElement[] items = array.toArray(new LookupElement[array.size()]);

    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).createLookup(editor, items, prefix, LookupArranger.DEFAULT);
    lookup.addLookupListener(new MyLookupAdapter(project, editor, null));
    lookup.show();
  }

  private static String computePrefix(TemplateImpl template, String argument) {
    String key = template.getKey();
    if (argument == null) {
      return key;
    }
    if (key.length() > 0 && Character.isJavaIdentifierPart(key.charAt(key.length() - 1))) {
      return key + ' ' + argument;
    }
    return key + argument;
  }

  public static void showTemplatesLookup(final Project project,
                                         final Editor editor,
                                         Map<TemplateImpl, String> template2Argument) {
    ArrayList<LookupItem> array = new ArrayList<LookupItem>();
    for (TemplateImpl template : template2Argument.keySet()) {
      String argument = template2Argument.get(template);
      String prefix = computePrefix(template, argument);
      LookupItem item = new LookupItem(template, prefix);
      item.setPrefixMatcher(new CamelHumpMatcher(prefix));
      array.add(item);
    }
    LookupElement[] items = array.toArray(new LookupElement[array.size()]);

    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).createLookup(editor, items, null, LookupArranger.DEFAULT);
    lookup.addLookupListener(new MyLookupAdapter(project, editor, template2Argument));
    lookup.show();
  }

  public boolean startInWriteAction() {
    return true;
  }

  private String getPrefix(Document document, int offset) {
    CharSequence chars = document.getCharsSequence();
    int start = offset;
    while (true) {
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

  private static class MyLookupAdapter extends LookupAdapter {
    private final Project myProject;
    private final Editor myEditor;
    private final Map<TemplateImpl, String> myTemplate2Argument;

    public MyLookupAdapter(Project project, Editor editor, Map<TemplateImpl, String> template2Argument) {
      myProject = project;
      myEditor = editor;
      myTemplate2Argument = template2Argument;
    }

    public void itemSelected(LookupEvent event) {
      final TemplateImpl template = (TemplateImpl)event.getItem().getObject();
      final String argument = myTemplate2Argument != null ? myTemplate2Argument.get(template) : null;
      new WriteCommandAction(myProject) {
        protected void run(Result result) throws Throwable {
          ((TemplateManagerImpl)TemplateManager.getInstance(myProject)).startTemplateWithPrefix(myEditor, template, null, argument);
        }
      }.execute();
    }
  }
}

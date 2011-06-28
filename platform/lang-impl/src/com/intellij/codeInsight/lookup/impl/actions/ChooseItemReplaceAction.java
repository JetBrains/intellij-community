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

package com.intellij.codeInsight.lookup.impl.actions;

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.ListTemplatesHandler;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class ChooseItemReplaceAction extends EditorAction {
  public ChooseItemReplaceAction(){
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_REPLACE);
      ChooseItemAction.getLookup(editor).finishLookup(Lookup.REPLACE_SELECT_CHAR);
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      if (lookup != null) {
        lookup.refreshUi(); // to bring the list model up to date

        CompletionProcess completion = CompletionService.getCompletionService().getCurrentCompletion();
        if (completion != null && completion.isAutopopupCompletion() && hasTemplatePrefix(lookup, TemplateSettings.TAB_CHAR) && !lookup.isSelectionTouched()) {
          return false;
        }

        if (lookup.isFocused()) {
          return true;
        }

        return !lookup.getItems().isEmpty();
      }
      return false;
    }
  }

  static boolean hasTemplatePrefix(LookupImpl lookup, char shortcutChar) {
    final PsiFile file = lookup.getPsiFile();
    if (file == null) return false;

    final Editor editor = lookup.getEditor();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());

    final int end = editor.getCaretModel().getOffset();
    final int start = lookup.getLookupStart();
    final String prefix = !lookup.getItems().isEmpty() ? editor.getDocument().getText(TextRange.create(start, end)) : ListTemplatesHandler.getPrefix(editor.getDocument(), end);
    final TemplateImpl template = LiveTemplateCompletionContributor.findApplicableTemplate(file, end, prefix);
    return template != null && shortcutChar == TemplateSettings.getInstance().getShortcutChar(template);
  }

}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.OffsetMap;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.template.TemplateLookupSelectionHandler;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author peter
*/
class TemplateExpressionLookupElement extends LookupElementDecorator<LookupElement> {
  private final TemplateState myState;

  public TemplateExpressionLookupElement(final TemplateState state, LookupElement element, int index) {
    super(PrioritizedLookupElement.withPriority(element, Integer.MAX_VALUE - 10 - index));
    myState = state;
  }

  private static InsertionContext createInsertionContext(LookupElement item,
                                                         PsiFile psiFile,
                                                         List<? extends LookupElement> elements,
                                                         Editor editor, final char completionChar) {
    final OffsetMap offsetMap = new OffsetMap(editor.getDocument());
    final InsertionContext context = new InsertionContext(offsetMap, completionChar, elements.toArray(new LookupElement[elements.size()]), psiFile, editor, false);
    context.setTailOffset(editor.getCaretModel().getOffset());
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, context.getTailOffset() - item.getLookupString().length());
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, context.getTailOffset());
    offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, context.getTailOffset());
    return context;
  }

  void handleTemplateInsert(List<? extends LookupElement> elements, final char completionChar) {
    final InsertionContext context = createInsertionContext(this, myState.getPsiFile(), elements, myState.getEditor(), completionChar);
    new WriteCommandAction(context.getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        handleInsert(context);
      }
    }.execute();
    Disposer.dispose(context.getOffsetMap());
  }

  @Override
  public void handleInsert(final InsertionContext context) {
    LookupElement item = getDelegate();
    Project project = context.getProject();
    Editor editor = context.getEditor();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    TextRange range = myState.getCurrentVariableRange();
    final TemplateLookupSelectionHandler handler = item.getUserData(TemplateLookupSelectionHandler.KEY_IN_LOOKUP_ITEM);
    if (handler != null && range != null) {
      handler.itemSelected(item, context.getFile(), context.getDocument(), range.getStartOffset(), range.getEndOffset());
    }
    else {
      super.handleInsert(context);
    }

    if (context.getCompletionChar() == '.') {
      EditorModificationUtil.insertStringAtCaret(editor, ".");
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      return;
    }

    if (!myState.isFinished()) {
      myState.calcResults(true);
    }

    myState.nextTab();
  }
}

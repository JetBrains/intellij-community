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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class TemplateInsertHandler implements InsertHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.TemplateInsertHandler");

  protected static final Object EXPANDED_TEMPLATE_ATTR = Key.create("EXPANDED_TEMPLATE_ATTR");

  public void handleInsert(final InsertionContext context, final LookupElement item) {
    context.setAddCompletionChar(false);
    if (isTemplateToBeCompleted(item)) {
      handleTemplate((LookupItem) item, context);
    }
  }

  protected static boolean isTemplateToBeCompleted(final LookupElement lookupItem) {
    return lookupItem instanceof LookupItem && lookupItem.getObject() instanceof Template
           && ((LookupItem)lookupItem).getAttribute(EXPANDED_TEMPLATE_ATTR) == null;
  }

  protected void handleTemplate(@NotNull final LookupItem lookupItem,
                                @NotNull final InsertionContext context) {
    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());

    Template template = (Template)lookupItem.getObject();

    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();

    final int templateStartOffset = context.getStartOffset();
    document.replaceString(templateStartOffset, templateStartOffset + lookupItem.getLookupString().length(), "");

    final RangeMarker offsetRangeMarker = document.createRangeMarker(templateStartOffset, templateStartOffset);

    TemplateManager.getInstance(editor.getProject()).startTemplate(editor, template, new TemplateEditingAdapter() {
      public void templateFinished(Template template, boolean brokenOff) {
        lookupItem.setAttribute(EXPANDED_TEMPLATE_ATTR, Boolean.TRUE);

        if (!offsetRangeMarker.isValid()) return;

        final Editor editor = context.getEditor();
        final int startOffset = offsetRangeMarker.getStartOffset();
        final int endOffset = editor.getCaretModel().getOffset();
        String lookupString = editor.getDocument().getCharsSequence().subSequence(startOffset, endOffset).toString();
        lookupItem.setLookupString(lookupString);

        final OffsetMap offsetMap = context.getOffsetMap();
        offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, endOffset);
        offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, endOffset);

        final PsiFile psiFile = context.getFile();

        InsertionContext newContext =
            new InsertionContext(offsetMap, context.getCompletionChar(), LookupElement.EMPTY_ARRAY, psiFile, editor);

        populateInsertMap(psiFile, offsetMap);

        handleInsert(newContext, lookupItem);
      }
    });
  }

  protected void populateInsertMap(@NotNull final PsiFile file, @NotNull final OffsetMap offsetMap) {
  }

}

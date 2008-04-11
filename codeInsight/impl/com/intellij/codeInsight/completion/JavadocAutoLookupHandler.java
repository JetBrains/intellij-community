package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.editor.Editor;

/**
 *
 */
public class JavadocAutoLookupHandler extends CodeCompletionHandler{
  protected boolean isAutocompleteOnInvocation() {
    return false;
  }

  protected boolean isAutocompleteCommonPrefixOnInvocation() {
    return false;
  }

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData, final CompletionParameters parameters,
                                   final CompletionProgressIndicator indicator){
  }

  protected void doComplete(final int offset1, final int offset2, final CompletionContext context, final String dummyIdentifier,
                            final Editor editor, final int invocationCount) {
    PsiFile file = context.file;
    int offset = context.getStartOffset();

    PsiElement lastElement = file.findElementAt(offset - 1);
    if (lastElement == null || !StringUtil.endsWithChar(lastElement.getText(), '@')) return;

    super.doComplete(offset1, offset2, context, dummyIdentifier, editor, invocationCount);
  }
}

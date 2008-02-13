package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.text.StringUtil;

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

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData){
  }

  protected LookupData getLookupData(CompletionContext context){
    PsiFile file = context.file;
    int offset = context.getStartOffset();

    PsiElement lastElement = file.findElementAt(offset - 1);
    if (lastElement == null || !StringUtil.endsWithChar(lastElement.getText(), '@')) {
      return LookupData.EMPTY;
    }
    else{
      return super.getLookupData(context);
    }
  }
}

package com.intellij.codeInsight.completion;

import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.position.SuperParentFilter;
import com.intellij.psi.javadoc.PsiDocComment;

/**
 *
 */
public class DotAutoLookupHandler extends CodeCompletionHandler{
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
    int offset = context.startOffset;

    PsiElement lastElement = file.findElementAt(offset - 1);
    if (lastElement == null) {
      return LookupData.EMPTY;
    }

    //do not show lookup when typing varargs ellipsis
    final PsiElement prevSibling = lastElement.getPrevSibling();
    if (prevSibling == null || ".".equals(prevSibling.getText())) return LookupData.EMPTY;
    PsiElement parent = prevSibling;
    do {
      parent = parent.getParent();
    } while(parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiTypeElement);
    if (parent instanceof PsiParameterList) return LookupData.EMPTY;

    if (!".".equals(lastElement.getText()) && !"#".equals(lastElement.getText())) {
      return LookupData.EMPTY;
    }
    else{
      final PsiElement element = file.findElementAt(offset);
      if(element != null && "#".equals(lastElement.getText())
        && !new SuperParentFilter(new ClassFilter(PsiDocComment.class)).isAcceptable(element, element.getParent())){
        return LookupData.EMPTY;
      }
      return super.getLookupData(context);
    }
  }
}

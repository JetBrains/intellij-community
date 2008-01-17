package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author yole
 */
public class JavadocErrorFilter implements Condition<PsiErrorElement> {
  public boolean value(final PsiErrorElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null;
  }
}

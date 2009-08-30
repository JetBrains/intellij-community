package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavadocErrorFilter extends HighlightErrorFilter {

  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    return !value(element);
  }

  public static boolean value(final PsiErrorElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null;
  }
}

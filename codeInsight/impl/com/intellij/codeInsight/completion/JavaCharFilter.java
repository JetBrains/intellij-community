/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;

public class JavaCharFilter extends CharFilter {

  private static boolean isWithinLiteral(final Lookup lookup) {
    PsiElement psiElement = lookup.getPsiElement();
    return psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression;
  }

  public Result acceptChar(char c, @NotNull final String prefix, final Lookup lookup) {
    if (lookup.isCompletion() && c == '.' && isWithinLiteral(lookup)) return Result.ADD_TO_PREFIX;
    return null;
  }

}
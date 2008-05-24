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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;

public class DefaultCharFilter extends CharFilter {

  private static boolean isWithinLiteral(final Lookup lookup) {
    PsiElement psiElement = lookup.getPsiElement();
    return psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression;
  }

  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    for (final CharFilter extension : Extensions.getExtensions(EP_NAME)) {
      final Result result = extension.acceptChar(c, prefixLength, lookup);
      if (result != null) {
        return result;                                                                                          
      }
    }

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    switch(c){
      case '.': if (isWithinLiteral(lookup)) return Result.ADD_TO_PREFIX;
      case ',':
      case ';':
      case '=':
      case ' ':
      case ':':
      case '(':
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;

      default:
        return Result.HIDE_LOOKUP;
    }
  }

}

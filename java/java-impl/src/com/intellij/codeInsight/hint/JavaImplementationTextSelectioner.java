/*
 * User: anna
 * Date: 01-Feb-2008
 */
package com.intellij.codeInsight.hint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

public class JavaImplementationTextSelectioner implements ImplementationTextSelectioner {
  private static final Logger LOG = Logger.getInstance("#" + JavaImplementationTextSelectioner.class.getName());

  public int getTextStartOffset(@NotNull final PsiElement parent) {
      PsiElement element = parent;
      if (element instanceof PsiDocCommentOwner) {
        PsiDocComment comment = ((PsiDocCommentOwner)element).getDocComment();
        if (comment != null) {
          element = comment.getNextSibling();
          while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
          }
        }
      }

      if (element != null) {
        return element.getTextRange().getStartOffset();
      }
      else {
        LOG.assertTrue(false, "Element should not be null: " + parent.getText());
        return parent.getTextRange().getStartOffset();
      }
    }

    public int getTextEndOffset(@NotNull PsiElement element) {
      return element.getTextRange().getEndOffset();
    }
}
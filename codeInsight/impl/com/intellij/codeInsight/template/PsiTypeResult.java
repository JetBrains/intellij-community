package com.intellij.codeInsight.template;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max, dsl
 */
public class PsiTypeResult implements Result {
  private final SmartTypePointer myTypePointer;
  private PsiManager myPsiManager;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.PsiTypeResult");

  public PsiTypeResult(@NotNull PsiType type, PsiManager manager) {
    final PsiType actualType = PsiUtil.convertAnonymousToBaseType(type);
    myTypePointer = SmartPointerManager.getInstance(manager.getProject()).createSmartTypePointer(actualType);
    myPsiManager = manager;
  }

  public PsiType getType() {
    return myTypePointer.getType();
  }

  public boolean equalsToText(String text, PsiElement context) {
    if (text.length() == 0) return false;
    final PsiType type = getType();
    if (text.equals(type.getCanonicalText())) return true;
    try {
      PsiTypeCastExpression cast = (PsiTypeCastExpression)myPsiManager.getElementFactory().createExpressionFromText("(" + text + ")a", context);
      final PsiTypeElement castType = cast.getCastType();
      if (castType == null) return false;
      return castType.getType().equals(type);
    }
    catch (IncorrectOperationException e) {
      // Indeed, not equal if cannot parse to a type.
      return false;
    }
  }

  public String toString() {
    return getType().getCanonicalText();
  }
}

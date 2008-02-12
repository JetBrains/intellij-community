package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max, dsl
 */
public class PsiTypeResult implements RecalculatableResult {
  private final SmartTypePointer myTypePointer;
  private JavaPsiFacade myFacade;

  public PsiTypeResult(@NotNull PsiType type, Project project) {
    final PsiType actualType = PsiUtil.convertAnonymousToBaseType(type);
    myTypePointer = SmartTypePointerManager.getInstance(project).createSmartTypePointer(actualType);
    myFacade = JavaPsiFacade.getInstance(project);
  }

  public PsiType getType() {
    return myTypePointer.getType();
  }

  public boolean equalsToText(String text, PsiElement context) {
    if (text.length() == 0) return false;
    final PsiType type = getType();
    if (text.equals(type.getCanonicalText())) return true;
    try {
      PsiTypeCastExpression cast = (PsiTypeCastExpression)myFacade.getElementFactory().createExpressionFromText("(" + text + ")a", context);
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

  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(getType(), psiFile, document, segmentStart, segmentEnd);
  }

  public void handleRecalc(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(getType(), psiFile, document, segmentStart, segmentEnd);
  }
}

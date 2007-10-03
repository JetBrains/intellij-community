/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

public class CreateConstantFieldFromUsageFix extends CreateFieldFromUsageFix {
  protected boolean createConstantField() {
    return true;
  }

  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    String refName = myReferenceExpression.getReferenceName();
    return refName.toUpperCase().equals(refName);
  }

  public CreateConstantFieldFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected String getText(String varName) {
    return QuickFixBundle.message("create.constant.from.usage.text", varName);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constant.from.usage.family");
  }
}
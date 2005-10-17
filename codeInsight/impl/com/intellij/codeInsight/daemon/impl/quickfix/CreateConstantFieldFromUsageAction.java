/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiReferenceExpression;

public class CreateConstantFieldFromUsageAction extends CreateFieldFromUsageAction {
  protected boolean createConstantField() {
    return true;
  }

  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    String refName = myReferenceExpression.getReferenceName();
    return refName.toUpperCase().equals(refName);
  }

  public CreateConstantFieldFromUsageAction(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected String getText(String varName) {
    return QuickFixBundle.message("create.constant.from.usage.text", varName);
  }

  public String getFamilyName() {
    return QuickFixBundle.message("create.constant.from.usage.family");
  }
}
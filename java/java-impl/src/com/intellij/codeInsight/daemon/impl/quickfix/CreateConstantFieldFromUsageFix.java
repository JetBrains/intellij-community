// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

@Deprecated
@ScheduledForRemoval(inVersion = "2019.3")
public class CreateConstantFieldFromUsageFix extends CreateFieldFromUsageFix {
  @Override
  protected boolean createConstantField() {
    return true;
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    String refName = myReferenceExpression.getReferenceName();
    return StringUtil.toUpperCase(refName).equals(refName);
  }

  public CreateConstantFieldFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  @Override
  protected String getText(String varName) {
    return QuickFixBundle.message("create.constant.from.usage.text", varName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constant.from.usage.family");
  }
}
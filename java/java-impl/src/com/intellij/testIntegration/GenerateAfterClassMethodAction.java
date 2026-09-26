// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration;

import com.intellij.psi.PsiClass;

public class GenerateAfterClassMethodAction extends BaseGenerateTestSupportMethodAction {
  public GenerateAfterClassMethodAction() {
    super(TestIntegrationUtils.MethodKind.AFTER_CLASS);
  }

  @Override
  protected boolean isValidFor(PsiClass targetClass, TestFramework framework) {
    return super.isValidFor(targetClass, framework) && framework.findAfterClassMethod(targetClass) == null;
  }
}

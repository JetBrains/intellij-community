// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.psi.PsiClass;
import com.intellij.util.Range;

public class ConstructorStepMethodFilter extends BasicStepMethodFilter {
  public ConstructorStepMethodFilter(JVMName classJvmName, Range<Integer> callingExpressionLines) {
    super(classJvmName, JVMNameUtil.CONSTRUCTOR_NAME, null, 0, callingExpressionLines, false);
  }

  public ConstructorStepMethodFilter(PsiClass psiClass, Range<Integer> callingExpressionLines) {
    this(JVMNameUtil.getJVMQualifiedName(psiClass), callingExpressionLines);
  }
}

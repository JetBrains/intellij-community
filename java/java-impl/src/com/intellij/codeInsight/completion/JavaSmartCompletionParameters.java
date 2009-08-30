/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiType;
import com.intellij.codeInsight.ExpectedTypeInfo;

/**
 * @author peter
 */
public class JavaSmartCompletionParameters extends CompletionParameters{
  private final ExpectedTypeInfo myExpectedType;

  public JavaSmartCompletionParameters(CompletionParameters parameters, final ExpectedTypeInfo expectedType) {
    super(parameters.getPosition(), parameters.getOriginalFile(), parameters.getCompletionType(), parameters.getOffset(), parameters.getInvocationCount());
    myExpectedType = expectedType;
  }

  public PsiType getExpectedType() {
    return myExpectedType.getType();
  }

  public PsiType getDefaultType() {
    return myExpectedType.getDefaultType();
  }
}

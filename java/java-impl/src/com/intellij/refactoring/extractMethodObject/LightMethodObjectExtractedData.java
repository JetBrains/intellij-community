// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.ApiStatus;

public class LightMethodObjectExtractedData {
  public static final Key<PsiMethod> REFERENCE_METHOD = Key.create("CompilingEvaluatorReferenceMethod");
  public static final Key<PsiType> REFERENCED_TYPE = Key.create("CompilingEvaluatorReferencedType");
  private final String myGeneratedCallText;
  private final PsiClass myGeneratedInnerClass;
  private final PsiElement myAnchor;
  private final boolean myUseMagicAccessor;
  private final boolean myHasOutputVariables;

  @ApiStatus.Internal
  public LightMethodObjectExtractedData(String generatedCallText,
                                        PsiClass generatedInnerClass,
                                        PsiElement anchor,
                                        boolean useMagicAccessor,
                                        boolean hasOutputVariables) {
    myGeneratedCallText = generatedCallText;
    myGeneratedInnerClass = generatedInnerClass;
    myAnchor = anchor;
    myUseMagicAccessor = useMagicAccessor;
    myHasOutputVariables = hasOutputVariables;
  }

  public PsiElement getAnchor() {
    return myAnchor;
  }

  public String getGeneratedCallText() {
    return myGeneratedCallText;
  }

  public PsiClass getGeneratedInnerClass() {
    return myGeneratedInnerClass;
  }

  public boolean useMagicAccessor() {
    return myUseMagicAccessor;
  }

  @ApiStatus.Internal
  public boolean hasOutputVariables() {
    return myHasOutputVariables;
  }
}

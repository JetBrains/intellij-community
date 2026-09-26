// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.stubs.StubElement;

public interface PsiAnnotationStub extends StubElement<PsiAnnotation> {
  PsiAnnotationStub[] EMPTY_ARRAY = new PsiAnnotationStub[0];

  String getText();

  PsiAnnotation getPsiElement();
}
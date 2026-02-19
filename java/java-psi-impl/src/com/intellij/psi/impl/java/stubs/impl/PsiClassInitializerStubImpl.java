// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassInitializerStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiClassInitializerStubImpl extends StubBase<PsiClassInitializer> implements PsiClassInitializerStub {
  public PsiClassInitializerStubImpl(final StubElement parent) {
    super(parent, JavaStubElementTypes.CLASS_INITIALIZER);
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiClassInitializerStub";
  }
}
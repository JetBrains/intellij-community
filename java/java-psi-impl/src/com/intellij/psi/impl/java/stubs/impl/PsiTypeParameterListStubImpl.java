// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiTypeParameterListStubImpl extends StubBase<PsiTypeParameterList> implements PsiTypeParameterListStub{
  public PsiTypeParameterListStubImpl(final StubElement parent) {
    super(parent, JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiTypeParameterListStub";
  }
}
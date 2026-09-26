// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiModifierListStubImpl extends StubBase<PsiModifierList> implements PsiModifierListStub {
  private final int myMask;

  public PsiModifierListStubImpl(final StubElement parent, final int mask) {
    super(parent, JavaStubElementTypes.MODIFIER_LIST);
    myMask = mask;
  }

  @Override
  public int getModifiersMask() {
    return myMask;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiModifierListStub[" + "mask=" + getModifiersMask() + "]";
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRecordHeaderStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiRecordHeaderStubImpl extends StubBase<PsiRecordHeader> implements PsiRecordHeaderStub {
  public PsiRecordHeaderStubImpl(final StubElement parent) {
    super(parent, JavaStubElementTypes.RECORD_HEADER);
  }

  @Override
  public String toString() {
    return "PsiRecordHeaderStub";
  }
}
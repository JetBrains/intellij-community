// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiJavaModuleStubImpl extends StubBase<PsiJavaModule> implements PsiJavaModuleStub {
  private final String myName;
  private final int myResolution;

  public PsiJavaModuleStubImpl(StubElement parent, String name, int resolution) {
    super(parent, JavaStubElementTypes.MODULE);
    myName = name;
    myResolution = resolution;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public int getResolution() {
    return myResolution;
  }

  @Override
  public String toString() {
    return "PsiJavaModuleStub[name=" + getName() + ", resolution=" + myResolution + "]";
  }
}
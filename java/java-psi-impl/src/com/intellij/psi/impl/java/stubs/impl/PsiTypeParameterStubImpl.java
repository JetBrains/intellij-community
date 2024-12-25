// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class PsiTypeParameterStubImpl extends StubBase<PsiTypeParameter> implements PsiTypeParameterStub {
  private final String myName;

  public PsiTypeParameterStubImpl(StubElement parent, String name) {
    super(parent, JavaStubElementTypes.TYPE_PARAMETER);
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiTypeParameter[" + myName + ']';
  }

  @Override
  public @Unmodifiable @NotNull List<PsiAnnotationStub> getAnnotations() {
    List<StubElement<?>> children = getChildrenStubs();

    return ContainerUtil.mapNotNull(children,
                                    stubElement -> stubElement instanceof PsiAnnotationStub ? (PsiAnnotationStub)stubElement : null);
  }
}
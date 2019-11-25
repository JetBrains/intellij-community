// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRecordComponentStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PsiRecordComponentStubImpl extends StubBase<PsiRecordComponent> implements PsiRecordComponentStub {
  private final String myName;
  private final TypeInfo myType;
  private final boolean myHasDeprecatedAnnotation;

  public PsiRecordComponentStubImpl(StubElement parent, @Nullable String name, @NotNull TypeInfo type, boolean annotation) {
    super(parent, JavaStubElementTypes.RECORD_COMPONENT);
    myName = name;
    myType = type;
    myHasDeprecatedAnnotation = annotation;
  }

  @Override
  @NotNull
  public TypeInfo getType(boolean doResolve) {
    return doResolve ? myType.applyAnnotations(this) : myType;
  }


  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean hasDeprecatedAnnotation() {
    return myHasDeprecatedAnnotation;
  }

  @Override
  public boolean hasDocComment() {
    return false;
  }


  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiRecordComponentStub[");

    if (hasDeprecatedAnnotation()) {
      builder.append("deprecated ");
    }

    builder.append(myName).append(':').append(myType);

    builder.append(']');
    return builder.toString();
  }
}
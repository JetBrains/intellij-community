// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiNameValuePairStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PsiNameValuePairStubImpl extends StubBase<PsiNameValuePair> implements PsiNameValuePairStub {

  private final @Nullable String myName;
  private final @Nullable String myValue;

  public PsiNameValuePairStubImpl(StubElement parent, @Nullable String name, @Nullable String value) {
    super(parent, JavaStubElementTypes.NAME_VALUE_PAIR);
    myName = name;
    myValue = value;
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }

  @Override
  public @Nullable String getValue() {
    return myValue;
  }
}

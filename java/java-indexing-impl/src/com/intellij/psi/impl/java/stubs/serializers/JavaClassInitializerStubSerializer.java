// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.serializers;

import com.intellij.psi.impl.java.stubs.impl.PsiClassInitializerStubImpl;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;

public class JavaClassInitializerStubSerializer implements StubSerializer<PsiClassInitializerStubImpl> {
  @NotNull private final IJavaElementType myType;

  public JavaClassInitializerStubSerializer(@NotNull IJavaElementType elementType) {
    myType = elementType;
  }

  @Override
  public void serialize(@NotNull PsiClassInitializerStubImpl stub, @NotNull StubOutputStream dataStream) {
    // Empty stub - nothing to serialize
  }

  @Override
  public @NotNull PsiClassInitializerStubImpl deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull PsiClassInitializerStubImpl stub, @NotNull IndexSink sink) { }

  @Override
  public @NotNull String getExternalId() {
    return "java." + myType;
  }
}
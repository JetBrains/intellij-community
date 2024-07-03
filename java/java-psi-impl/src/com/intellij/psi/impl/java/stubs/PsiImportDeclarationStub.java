// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportDeclaration;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.Nullable;

public interface PsiImportDeclarationStub<T extends PsiImportDeclaration, R extends PsiElement> extends StubElement<T> {
  @Nullable
  String getImportReferenceText();
  @Nullable
  R getReference();
}
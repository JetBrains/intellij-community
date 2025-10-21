// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiPackageStatementStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.notNull;

public class PsiPackageStatementStubImpl extends StubBase<PsiPackageStatement> implements PsiPackageStatementStub {
  private final @NotNull String myPackageName;

  public PsiPackageStatementStubImpl(StubElement parent, String packageName) {
    super(parent, JavaStubElementTypes.PACKAGE_STATEMENT);
    myPackageName = notNull(packageName, "");
  }

  @Override
  public @NotNull String getPackageName() {
    return myPackageName;
  }

  @Override
  public String toString() {
    return "PsiPackageStatementStub[" + myPackageName + "]";
  }
}
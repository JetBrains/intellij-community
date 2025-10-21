// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public interface PsiPackageStatementStub extends StubElement<PsiPackageStatement> {
  /**
   * @return package name declared in this package statement; empty string if declaration is malformed
   */
  @NotNull String getPackageName();
}
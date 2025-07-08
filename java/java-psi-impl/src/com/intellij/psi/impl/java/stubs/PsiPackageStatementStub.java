// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.stubs.StubElement;

public interface PsiPackageStatementStub extends StubElement<PsiPackageStatement> {
  String getPackageName();
}
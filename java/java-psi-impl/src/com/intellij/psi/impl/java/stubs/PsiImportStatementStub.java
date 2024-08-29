// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.Nullable;

public interface PsiImportStatementStub extends StubElement<PsiImportStatementBase> {
  boolean isStatic();
  boolean isOnDemand();
  boolean isModule();

  @Nullable
  String getImportReferenceText();

  @Nullable
  PsiJavaCodeReferenceElement getReference();

  @Nullable PsiJavaModuleReferenceElement getModuleReference();
}
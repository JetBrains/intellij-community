// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;

public interface PsiImportStatementStub extends PsiImportDeclarationStub<PsiImportStatementBase, PsiJavaCodeReferenceElement> {
  boolean isStatic();
  boolean isOnDemand();
}
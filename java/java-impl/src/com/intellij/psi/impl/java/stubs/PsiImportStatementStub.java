/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.Nullable;

public interface PsiImportStatementStub extends StubElement<PsiImportStatementBase> {
  boolean isStatic();
  boolean isOnDemand();
  String getImportReferenceText();
  @Nullable
  PsiJavaCodeReferenceElement getReference();
}
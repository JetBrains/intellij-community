/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;

public interface StubBasedPsiElement<Stub extends StubElement> extends PsiElement {
  IStubElementType getElementType();
  Stub getStub();
}
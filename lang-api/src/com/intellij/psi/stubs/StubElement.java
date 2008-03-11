/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;

import java.util.List;

public interface StubElement<T extends PsiElement> {
  IStubElementType getStubType();
  StubElement getParentStub();
  List<StubElement> getChildrenStubs();

  T getPsi();
}
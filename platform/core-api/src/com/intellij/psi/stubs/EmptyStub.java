package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;

/**
 * @author peter
 */
public class EmptyStub<T extends PsiElement> extends StubBase<T> {

  public EmptyStub(StubElement parent, IStubElementType elementType) {
    super(parent, elementType);
  }
}

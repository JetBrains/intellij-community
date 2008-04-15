package com.intellij.psi.stubs;

import com.intellij.psi.PsiNamedElement;

/**
 * @author yole
 */
public abstract class NamedStubBase<T extends PsiNamedElement> extends StubBase<T> implements NamedStub<T> {
  protected final String myName;

  protected NamedStubBase(final StubElement parent, final IStubElementType elementType, final String name) {
    super(parent, elementType);
    myName = name;
  }

  public String getName() {
    return myName;
  }
}

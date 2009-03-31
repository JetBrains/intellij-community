package com.intellij.psi.stubs;

import com.intellij.psi.PsiNamedElement;
import com.intellij.util.io.StringRef;

/**
 * @author yole
 */
public abstract class NamedStubBase<T extends PsiNamedElement> extends StubBase<T> implements NamedStub<T> {
  private final StringRef myName;

  protected NamedStubBase(StubElement parent, IStubElementType elementType, StringRef name) {
    super(parent, elementType);
    myName = name;
  }

  protected NamedStubBase(final StubElement parent, final IStubElementType elementType, final String name) {
    this(parent, elementType, StringRef.fromString(name));
  }

  public String getName() {
    return myName.getString();
  }
}

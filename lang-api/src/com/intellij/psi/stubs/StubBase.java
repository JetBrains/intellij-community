/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;

import java.util.ArrayList;
import java.util.List;

public abstract class StubBase<T extends PsiElement> implements StubElement<T> {
  private final StubElement myParent;
  private final List<StubElement> myChildren = new ArrayList<StubElement>();
  private final IStubElementType myElementType;
  private volatile T myPsi;

  protected StubBase(final StubElement parent, final IStubElementType elementType) {
    myParent = parent;
    myElementType = elementType;
    if (parent != null) {
      ((StubBase)parent).myChildren.add(this);
    }
  }

  public StubElement getParentStub() {
    return myParent;
  }

  public List<StubElement> getChildrenStubs() {
    return myChildren;
  }

  public void setPsi(final T psi) {
    myPsi = psi;
  }

  public T getPsi() {
    if (myPsi != null) return myPsi;

    synchronized (PsiLock.LOCK) {
      if (myPsi != null) return myPsi;

      //noinspection unchecked
      myPsi = (T)getStubType().createPsi(this);
    }

    return myPsi;
  }

  public IStubElementType getStubType() {
    return myElementType;
  }
}
/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class StubBase<T extends PsiElement> extends UserDataHolderBase implements StubElement<T> {
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

  @Nullable
  public <P extends PsiElement> StubElement<P> findChildStubByType(final IStubElementType<?, P> elementType) {
    for (StubElement childStub : getChildrenStubs()) {
      if (childStub.getStubType() == elementType) {
        return childStub;
      }
    }
    return null;
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

  public <E extends PsiElement> E[] getChildrenByType(final IElementType elementType, final E[] array) {
    List<E> result = new ArrayList<E>();
    for(StubElement childStub: getChildrenStubs()) {
      if (childStub.getStubType() == elementType) {
        //noinspection unchecked
        result.add((E)childStub.getPsi());
      }
    }
    return result.toArray(array);
  }

  public <E extends PsiElement> E[] getChildrenByType(final TokenSet filter, final E[] array) {
    List<E> result = new ArrayList<E>();
    for(StubElement childStub: getChildrenStubs()) {
      if (filter.contains(childStub.getStubType())) {
        //noinspection unchecked
        result.add((E)childStub.getPsi());
      }
    }
    return result.toArray(array);
  }

  @Nullable
  public <E extends PsiElement> E getParentStubOfType(final Class<E> parentClass) {
    StubElement parent = myParent;
    while(parent != null) {
      PsiElement psi = parent.getPsi();
      if (parentClass.isInstance(psi)) {
        //noinspection unchecked
        return (E)psi;
      }
      parent = parent.getParentStub();
    }
    return null;
  }

  public IStubElementType getStubType() {
    return myElementType;
  }

  public Project getProject() {
    return getPsi().getProject();
  }

  public String printTree() {
    StringBuilder builder = new StringBuilder();
    printTree(builder, 0);
    return builder.toString();
  }

  private void printTree(StringBuilder builder, int nestingLevel) {
    for (int i = 0; i < nestingLevel; i++) builder.append("  ");
    builder.append(toString()).append('\n');
    for (StubElement child : getChildrenStubs()) {
      ((StubBase)child).printTree(builder, nestingLevel + 1);
    }
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class StubBase<T extends PsiElement> extends ObjectStubBase<StubElement> implements StubElement<T> {
  private List<StubElement> myChildren;
  private final IStubElementType myElementType;
  private volatile T myPsi;

  private static final AtomicFieldUpdater<StubBase, PsiElement> ourPsiUpdater =
    AtomicFieldUpdater.forFieldOfType(StubBase.class, PsiElement.class);

  @SuppressWarnings("unchecked")
  protected StubBase(final StubElement parent, final IStubElementType elementType) {
    super(parent);
    myElementType = elementType;
    if (parent != null) {
      if (((StubBase)parent).myChildren == null)
        ((StubBase)parent).myChildren = new SmartList<StubElement>();
      ((StubBase)parent).myChildren.add(this);
    }
  }

  @Override
  public StubElement getParentStub() {
    return myParent;
  }

  @NotNull
  @Override
  @SuppressWarnings("unchecked")
  public List<StubElement> getChildrenStubs() {
    return ObjectUtils.chooseNotNull(myChildren, Collections.<StubElement>emptyList());
  }

  @Override
  @Nullable
  public <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(@NotNull IStubElementType<S, P> elementType) {
    List<StubElement> childrenStubs = getChildrenStubs();
    int size = childrenStubs.size();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; ++i) {
      StubElement childStub = childrenStubs.get(i);
      if (childStub.getStubType() == elementType) {
        @SuppressWarnings("unchecked") S stub = (S)childStub;
        return stub;
      }
    }

    return null;
  }

  public void setPsi(@NotNull final T psi) {
    myPsi = psi;
  }

  @Nullable
  final T getCachedPsi() {
    return myPsi;
  }

  @Override
  public T getPsi() {
    T psi = myPsi;
    if (psi != null) return psi;

    psi = (T)getStubType().createPsi(this);
    return ourPsiUpdater.compareAndSet(this, null, psi) ? psi : ObjectUtils.assertNotNull(myPsi);
  }

  @NotNull
  @Override
  public <E extends PsiElement> E[] getChildrenByType(@NotNull final IElementType elementType, E[] array) {
    final int count = countChildren(elementType);

    array = ArrayUtil.ensureExactSize(count, array);
    if (count == 0) return array;
    fillFilteredChildren(elementType, array);

    return array;
  }

  @NotNull
  @Override
  public <E extends PsiElement> E[] getChildrenByType(@NotNull final TokenSet filter, E[] array) {
    final int count = countChildren(filter);

    array = ArrayUtil.ensureExactSize(count, array);
    if (count == 0) return array;
    fillFilteredChildren(filter, array);

    return array;
  }

  @NotNull
  @Override
  public <E extends PsiElement> E[] getChildrenByType(@NotNull final IElementType elementType, @NotNull final ArrayFactory<E> f) {
    int count = countChildren(elementType);

    E[] result = f.create(count);
    if (count > 0) fillFilteredChildren(elementType, result);

    return result;
  }

  private int countChildren(final IElementType elementType) {
    int count = 0;
    List<StubElement> childrenStubs = getChildrenStubs();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement childStub = childrenStubs.get(i);
      if (childStub.getStubType() == elementType) count++;
    }

    return count;
  }

  private int countChildren(final TokenSet types) {
    int count = 0;
    List<StubElement> childrenStubs = getChildrenStubs();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement childStub = childrenStubs.get(i);
      if (types.contains(childStub.getStubType())) count++;
    }

    return count;
  }

  private <E extends PsiElement> void fillFilteredChildren(IElementType type, E[] result) {
    int count = 0;
    for (StubElement childStub : getChildrenStubs()) {
      if (childStub.getStubType() == type) {
        //noinspection unchecked
        result[count++] = (E)childStub.getPsi();
      }
    }

    assert count == result.length;
  }

  private <E extends PsiElement> void fillFilteredChildren(TokenSet set, E[] result) {
    int count = 0;
    for (StubElement childStub : getChildrenStubs()) {
      if (set.contains(childStub.getStubType())) {
        //noinspection unchecked
        result[count++] = (E)childStub.getPsi();
      }
    }

    assert count == result.length;
  }

  @NotNull
  @Override
  public <E extends PsiElement> E[] getChildrenByType(@NotNull final TokenSet filter, @NotNull final ArrayFactory<E> f) {
    final int count = countChildren(filter);

    E[] array = f.create(count);
    if (count == 0) return array;

    fillFilteredChildren(filter, array);

    return array;
  }

  @Override
  @Nullable
  public <E extends PsiElement> E getParentStubOfType(@NotNull final Class<E> parentClass) {
    StubElement parent = myParent;
    while (parent != null) {
      PsiElement psi = parent.getPsi();
      if (parentClass.isInstance(psi)) {
        //noinspection unchecked
        return (E)psi;
      }
      parent = parent.getParentStub();
    }
    return null;
  }

  @Override
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

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}

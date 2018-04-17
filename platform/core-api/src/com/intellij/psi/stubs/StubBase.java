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
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class StubBase<T extends PsiElement> extends ObjectStubBase<StubElement> implements StubElement<T> {
  StubList myStubList;
  private volatile T myPsi;

  private static final AtomicFieldUpdater<StubBase, PsiElement> ourPsiUpdater =
    AtomicFieldUpdater.forFieldOfType(StubBase.class, PsiElement.class);

  protected StubBase(StubElement parent, IStubElementType elementType) {
    super(parent);
    StubList stubList = parent == null ? new StubList(10) : ((StubBase)parent).myStubList;
    stubList.addStub(this, (StubBase<?>)parent, elementType);
  }

  @Override
  public StubElement getParentStub() {
    return myParent;
  }

  @NotNull
  @Override
  @SuppressWarnings("unchecked")
  public List<StubElement> getChildrenStubs() {
    return (List)myStubList.getChildrenStubs(id);
  }

  @Override
  @Nullable
  public <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(@NotNull IStubElementType<S, P> elementType) {
    return myStubList.findChildStubByType(id, elementType);
  }

  public void setPsi(@NotNull T psi) {
    assert myPsi == null || myPsi == psi;
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

    //noinspection unchecked
    psi = (T)getStubType().createPsi(this);
    return ourPsiUpdater.compareAndSet(this, null, psi) ? psi : ObjectUtils.assertNotNull(myPsi);
  }

  @NotNull
  @Override
  public <E extends PsiElement> E[] getChildrenByType(@NotNull final IElementType elementType, E[] array) {
    List<StubElement> childrenStubs = getChildrenStubs();
    int count = countChildren(elementType, childrenStubs);

    array = ArrayUtil.ensureExactSize(count, array);
    if (count == 0) return array;
    fillFilteredChildren(elementType, array, childrenStubs);

    return array;
  }

  @NotNull
  @Override
  public <E extends PsiElement> E[] getChildrenByType(@NotNull final TokenSet filter, E[] array) {
    List<StubElement> childrenStubs = getChildrenStubs();
    int count = countChildren(filter, childrenStubs);

    array = ArrayUtil.ensureExactSize(count, array);
    if (count == 0) return array;
    fillFilteredChildren(filter, array, childrenStubs);

    return array;
  }

  @NotNull
  @Override
  public <E extends PsiElement> E[] getChildrenByType(@NotNull final IElementType elementType, @NotNull final ArrayFactory<E> f) {
    List<StubElement> childrenStubs = getChildrenStubs();
    int count = countChildren(elementType, childrenStubs);

    E[] result = f.create(count);
    if (count > 0) fillFilteredChildren(elementType, result, childrenStubs);

    return result;
  }

  private static int countChildren(IElementType elementType, List<StubElement> childrenStubs) {
    int count = 0;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement childStub = childrenStubs.get(i);
      if (childStub.getStubType() == elementType) count++;
    }

    return count;
  }

  private static int countChildren(TokenSet types, List<StubElement> childrenStubs) {
    int count = 0;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement childStub = childrenStubs.get(i);
      if (types.contains(childStub.getStubType())) count++;
    }

    return count;
  }

  private static <E extends PsiElement> void fillFilteredChildren(IElementType type, E[] result, List<StubElement> childrenStubs) {
    int count = 0;
    for (StubElement childStub : childrenStubs) {
      if (childStub.getStubType() == type) {
        //noinspection unchecked
        result[count++] = (E)childStub.getPsi();
      }
    }

    assert count == result.length;
  }

  private static <E extends PsiElement> void fillFilteredChildren(TokenSet set, E[] result, List<StubElement> childrenStubs) {
    int count = 0;
    for (StubElement childStub : childrenStubs) {
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
    List<StubElement> childrenStubs = getChildrenStubs();
    int count = countChildren(filter, childrenStubs);

    E[] array = f.create(count);
    if (count == 0) return array;

    fillFilteredChildren(filter, array, childrenStubs);

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
    return myStubList.getStubType(id);
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

  /**
   * @return comparison result (as in {@link Comparable}) of this stub with {@code another},
   * where "a<b" means that "a" occurs before "b" in the deep-first traversal of the stub tree,
   * and the same holds for their AST equivalents.
   */
  public int compareByOrderWith(ObjectStubBase<?> another) {
    return Integer.compare(getStubId(), another.getStubId());
  }

}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class StubBase<T extends PsiElement> extends ObjectStubBase<StubElement<?>> implements StubElement<T> {
  // todo remove once we don't need this for stub-ast mismatch debug info
  private static volatile boolean ourStubReloadingProhibited;
  private StubList myStubList;
  private volatile T myPsi;

  @SuppressWarnings("unchecked")
  private static final AtomicReferenceFieldUpdater<StubBase<?>, PsiElement> myPsiUpdater =
    AtomicReferenceFieldUpdater.newUpdater((Class<StubBase<?>>)(Class)StubBase.class, PsiElement.class, "myPsi");

  protected StubBase(@Nullable StubElement parent, IStubElementType<?, ?> elementType) {
    this(parent, (IElementType)elementType);
  }

  @ApiStatus.Internal
  public StubList getStubList() {
    return myStubList;
  }

  @ApiStatus.Internal
  public void setStubList(StubList stubList) {
    myStubList = stubList;
  }

  @ApiStatus.Experimental
  protected StubBase(@Nullable StubElement parent, @Nullable IElementType elementType) {
    super(parent);
    myStubList = parent == null ? new MaterialStubList(10) : ((StubBase<?>)parent).myStubList;
    myStubList.addStub(this, (StubBase<?>)parent, elementType);
    if (parent == null && !(this instanceof PsiFileStub<?>)) {
      throw new PsiInvalidElementAccessException(getPsi(),
                                                 "stub hierarchy is invalid: the parent of " + this + " (" + getClass() + ")" +
                                                 " is null, even though it's not a PsiFileStub", null);
    }
  }

  @Override
  public StubElement<?> getParentStub() {
    return myParent;
  }

  @Override
  public PsiFileStub<?> getContainingFileStub() {
    StubBase<?> rootStub = myStubList.get(0);
    if (!(rootStub instanceof PsiFileStub)) {
      return null;
    }
    return (PsiFileStub<?>)rootStub;
  }

  @Override
  public @NotNull List<StubElement<?>> getChildrenStubs() {
    //noinspection unchecked
    return (List)myStubList.getChildrenStubs(id);
  }

  @Override
  public @Nullable <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(@NotNull IStubElementType<S, P> elementType) {
    return myStubList.findChildStubByType(id, elementType);
  }

  @ApiStatus.Experimental
  @Override
  public final @Nullable StubElement<PsiElement> findChildStubByElementType(@NotNull IElementType elementType) {
    return myStubList.findChildStubByType(id, elementType);
  }

  public void setPsi(@NotNull T psi) {
    assert myPsi == null || myPsi == psi;
    myPsi = psi;
  }

  private @Nullable T getCachedPsi() {
    return myPsi;
  }

  @Override
  public T getPsi() {
    T psi = myPsi;
    if (psi != null) return psi;

    StubElementRegistryService stubElementRegistryService = StubElementRegistryService.getInstance();
    IElementType elementType = getElementType();
    @SuppressWarnings("unchecked")
    StubElementFactory<StubBase<T>, T> factory = (StubElementFactory<StubBase<T>, T>)stubElementRegistryService.getStubFactory(elementType);
    if (factory == null) {
      throw new IllegalStateException("Stub factory is null for the element type: " + elementType);
    }
    psi = factory.createPsi(this);
    return myPsiUpdater.compareAndSet(this, null, psi) ? psi : Objects.requireNonNull(myPsi);
  }

  @Override
  public <E extends PsiElement> E @NotNull [] getChildrenByType(final @NotNull IElementType elementType, E[] array) {
    List<StubElement<?>> childrenStubs = getChildrenStubs();
    int count = countChildren(elementType, childrenStubs);

    array = ArrayUtil.ensureExactSize(count, array);
    if (count == 0) return array;
    fillFilteredChildren(elementType, array, childrenStubs);

    return array;
  }

  @Override
  public <E extends PsiElement> E @NotNull [] getChildrenByType(final @NotNull TokenSet filter, E[] array) {
    List<StubElement<?>> childrenStubs = getChildrenStubs();
    int count = countChildren(filter, childrenStubs);

    array = ArrayUtil.ensureExactSize(count, array);
    if (count == 0) return array;
    fillFilteredChildren(filter, array, childrenStubs);

    return array;
  }

  @Override
  public <E extends PsiElement> E @NotNull [] getChildrenByType(final @NotNull IElementType elementType,
                                                                final @NotNull ArrayFactory<? extends E> f) {
    List<StubElement<?>> childrenStubs = getChildrenStubs();
    int count = countChildren(elementType, childrenStubs);

    E[] result = f.create(count);
    if (count > 0) fillFilteredChildren(elementType, result, childrenStubs);

    return result;
  }

  private static int countChildren(IElementType elementType, List<? extends StubElement<?>> childrenStubs) {
    int count = 0;
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement<?> childStub = childrenStubs.get(i);
      if (childStub.getElementType() == elementType) count++;
    }

    return count;
  }

  private static int countChildren(TokenSet types, List<? extends StubElement<?>> childrenStubs) {
    int count = 0;
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement<?> childStub = childrenStubs.get(i);
      if (types.contains(childStub.getElementType())) count++;
    }

    return count;
  }

  private static <E extends PsiElement> void fillFilteredChildren(IElementType type,
                                                                  E[] result,
                                                                  List<? extends StubElement<?>> childrenStubs) {
    int count = 0;
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement<?> childStub = childrenStubs.get(i);
      if (childStub.getElementType() == type) {
        //noinspection unchecked
        result[count++] = (E)childStub.getPsi();
      }
    }

    assert count == result.length;
  }

  private static <E extends PsiElement> void fillFilteredChildren(TokenSet set, E[] result, List<? extends StubElement<?>> childrenStubs) {
    int count = 0;
    for (int i = 0, childrenStubsSize = childrenStubs.size(); i < childrenStubsSize; i++) {
      StubElement<?> childStub = childrenStubs.get(i);
      if (set.contains(childStub.getElementType())) {
        //noinspection unchecked
        result[count++] = (E)childStub.getPsi();
      }
    }

    assert count == result.length;
  }

  @Override
  public <E extends PsiElement> E @NotNull [] getChildrenByType(final @NotNull TokenSet filter,
                                                                final @NotNull ArrayFactory<? extends E> f) {
    List<StubElement<?>> childrenStubs = getChildrenStubs();
    int count = countChildren(filter, childrenStubs);

    E[] array = f.create(count);
    if (count == 0) return array;

    fillFilteredChildren(filter, array, childrenStubs);

    return array;
  }

  @Override
  public @Nullable <E extends PsiElement> E getParentStubOfType(final @NotNull Class<E> parentClass) {
    StubElement<?> parent = myParent;
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

  @Deprecated
  @Override
  public IStubElementType<?, ?> getStubType() {
    IElementType elementType = getElementType();
    if (elementType instanceof IStubElementType<?, ?>) {
      return (IStubElementType<?, ?>)elementType;
    }
    else {
      // todo IJPL-562 verify this assert makes sense
      throw new IllegalStateException("Stub type is null for the element type: " + elementType);
    }
  }

  @ApiStatus.Experimental
  @Override
  public IElementType getElementType() {
    return myStubList.getStubElementType(id);
  }

  @ApiStatus.Experimental
  @Override
  public ObjectStubSerializer<?, ? extends Stub> getStubSerializer() {
    IElementType elementType = getElementType();
    ObjectStubSerializer<?, @NotNull Stub> serializer = elementType != null ? StubElementRegistryService.getInstance().getStubSerializer(elementType) : null;
    if (serializer == null) {
      throw new IllegalStateException("Stub serializer is null for the element type: " + elementType + ", this =" + this);
    }
    return serializer;
  }

  public Project getProject() {
    return getPsi().getProject();
  }

  /**
   * Consider using {@link com.intellij.psi.impl.DebugUtil#stubTreeToString(Stub)}.
   */
  public String printTree() {
    StringBuilder builder = new StringBuilder();
    printTree(builder, 0);
    return builder.toString();
  }

  private void printTree(StringBuilder builder, int nestingLevel) {
    for (int i = 0; i < nestingLevel; i++) builder.append("  ");
    builder.append(this).append('\n');
    for (StubElement<?> child : getChildrenStubs()) {
      ((StubBase<?>)child).printTree(builder, nestingLevel + 1);
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

  @ApiStatus.Internal
  public static void checkDeserializationCreatesNoPsi(PsiFileStub<?> @NotNull [] roots) {
    if (ourStubReloadingProhibited) return;

    for (PsiFileStub<?> root : roots) {
      if (root instanceof StubBase) {
        StubList stubList = ((StubBase<?>)root).getStubList();
        for (int i = 0; i < stubList.size(); i++) {
          StubBase<?> each = stubList.getCachedStub(i);
          PsiElement cachedPsi = each == null ? null : each.getCachedPsi();
          if (cachedPsi != null) {
            ourStubReloadingProhibited = true;
            throw new AssertionError("Stub deserialization shouldn't create PSI: " + cachedPsi + "; " + each);
          }
        }
      }
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class StubTree extends ObjectStubTree<StubElement<?>> {
  private final StubSpine mySpine = new StubSpine(this);

  public StubTree(@NotNull PsiFileStub<?> root) {
    this(root, true);
  }

  public StubTree(@NotNull PsiFileStub<?> root, boolean withBackReference) {
    super((ObjectStubBase<?>)root, withBackReference);
  }

  @Override
  protected @Unmodifiable @NotNull List<StubElement<?>> enumerateStubs(@NotNull Stub root) {
    return ((StubBase<?>)root).getStubList().finalizeLoadingStage().toPlainList();
  }

  @Override
  final @Unmodifiable @NotNull List<StubElement<?>> getPlainListFromAllRoots() {
    PsiFileStub<?>[] roots = ((PsiFileStubImpl<?>)getRoot()).getStubRoots();
    if (roots.length == 1) return super.getPlainListFromAllRoots();

    return ContainerUtil.concat(roots, stub -> ((PsiFileStubImpl<?>)stub).getStubList().toPlainList());
  }

  @Override
  public @NotNull PsiFileStub<?> getRoot() {
    return (PsiFileStub<?>)myRoot;
  }

  public @NotNull StubbedSpine getSpine() {
    return mySpine;
  }
}

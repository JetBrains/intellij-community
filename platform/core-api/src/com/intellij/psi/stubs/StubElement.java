/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */
public interface StubElement<T extends PsiElement> extends Stub {
  @Override
  IStubElementType getStubType();
  @Override
  StubElement getParentStub();
  @Override
  @NotNull
  List<StubElement> getChildrenStubs();

  @Nullable
  <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(@NotNull IStubElementType<S, P> elementType);

  T getPsi();

  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull IElementType elementType, final E[] array);
  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull TokenSet filter, final E[] array);

  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull IElementType elementType, @NotNull ArrayFactory<E> f);
  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull TokenSet filter, @NotNull ArrayFactory<E> f);

  @Nullable
  <E extends PsiElement> E getParentStubOfType(@NotNull Class<E> parentClass);
}
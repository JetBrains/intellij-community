// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull IElementType elementType, final E[] array);
  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull TokenSet filter, final E[] array);

  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull IElementType elementType, @NotNull ArrayFactory<? extends E> f);
  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull TokenSet filter, @NotNull ArrayFactory<? extends E> f);

  @Nullable
  <E extends PsiElement> E getParentStubOfType(@NotNull Class<E> parentClass);
}
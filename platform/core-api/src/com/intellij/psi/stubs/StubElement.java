// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public interface StubElement<T extends PsiElement> extends Stub {
  /**
   * @deprecated Use {@link #getElementType()} or {@link #getStubSerializer()} instead
   */
  @Deprecated
  @Override
  IStubElementType<?, ?> getStubType();

  @ApiStatus.Experimental
  IElementType getElementType();

  @Override
  StubElement<?> getParentStub();

  @Nullable PsiFileStub<?> getContainingFileStub();

  @Override
  @NotNull
  @Unmodifiable
  List<StubElement<?>> getChildrenStubs();

  /**
   * @deprecated Use {@link #findChildStubByElementType} instead
   */
  @Deprecated
  @Nullable
  <P extends PsiElement, S extends StubElement<P>> S findChildStubByType(@NotNull IStubElementType<S, P> elementType);

  @ApiStatus.Experimental
  default @Nullable StubElement<? extends PsiElement> findChildStubByElementType(@NotNull IElementType elementType) {
    if (elementType instanceof IStubElementType) {
      return findChildStubByType((IStubElementType<StubElement<PsiElement>, PsiElement>)elementType);
    }
    else {
      return null;
    }
  }

  T getPsi();

  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull IElementType elementType, final E[] array);
  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull TokenSet filter, final E[] array);

  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull IElementType elementType, @NotNull ArrayFactory<? extends E> f);
  <E extends PsiElement> E @NotNull [] getChildrenByType(@NotNull TokenSet filter, @NotNull ArrayFactory<? extends E> f);

  @Nullable
  <E extends PsiElement> E getParentStubOfType(@NotNull Class<E> parentClass);
}
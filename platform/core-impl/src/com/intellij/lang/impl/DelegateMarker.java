// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DelegateMarker implements PsiBuilder.Marker {
  private final @NotNull PsiBuilder.Marker myDelegate;

  public DelegateMarker(@NotNull PsiBuilder.Marker delegate) {
    myDelegate = delegate;
  }

  public @NotNull PsiBuilder.Marker getDelegate() {
    return myDelegate;
  }

  @Override
  public @NotNull PsiBuilder.Marker precede() {
    return myDelegate.precede();
  }

  @Override
  public void drop() {
    myDelegate.drop();
  }

  @Override
  public void rollbackTo() {
    myDelegate.rollbackTo();
  }

  @Override
  public void done(@NotNull IElementType type) {
    myDelegate.done(type);
  }

  @Override
  public void collapse(@NotNull IElementType type) {
    myDelegate.collapse(type);
  }

  @Override
  public void doneBefore(@NotNull IElementType type, @NotNull PsiBuilder.Marker before) {
    myDelegate.doneBefore(type, before);
  }

  @Override
  public void doneBefore(@NotNull IElementType type, @NotNull PsiBuilder.Marker before, @NotNull @NlsContexts.ParsingError String errorMessage) {
    myDelegate.doneBefore(type, before, errorMessage);
  }

  @Override
  public void error(@NotNull @NlsContexts.ParsingError String message) {
    myDelegate.error(message);
  }

  @Override
  public void errorBefore(@NotNull @NlsContexts.ParsingError String message, @NotNull PsiBuilder.Marker before) {
    myDelegate.errorBefore(message, before);
  }

  @Override
  public void setCustomEdgeTokenBinders(@Nullable WhitespacesAndCommentsBinder left, @Nullable WhitespacesAndCommentsBinder right) {
    myDelegate.setCustomEdgeTokenBinders(left, right);
  }
}

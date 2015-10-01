/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.impl;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DelegateMarker implements PsiBuilder.Marker {
  @NotNull
  private final PsiBuilder.Marker myDelegate;

  public DelegateMarker(@NotNull PsiBuilder.Marker delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public PsiBuilder.Marker getDelegate() {
    return myDelegate;
  }

  @NotNull
  @Override
  public PsiBuilder.Marker precede() {
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
  public void doneBefore(@NotNull IElementType type, @NotNull PsiBuilder.Marker before, String errorMessage) {
    myDelegate.doneBefore(type, before, errorMessage);
  }

  @Override
  public void error(String message) {
    myDelegate.error(message);
  }

  @Override
  public void errorBefore(String message, @NotNull PsiBuilder.Marker before) {
    myDelegate.errorBefore(message, before);
  }

  @Override
  public void setCustomEdgeTokenBinders(@Nullable WhitespacesAndCommentsBinder left, @Nullable WhitespacesAndCommentsBinder right) {
    myDelegate.setCustomEdgeTokenBinders(left, right);
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Default empty implementation of {@link PsiTreeChangeListener}.
 */
public abstract class PsiTreeChangeAdapter implements PsiTreeChangeListener {
  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
  }
}

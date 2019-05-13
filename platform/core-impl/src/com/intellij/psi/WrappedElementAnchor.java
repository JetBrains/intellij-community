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
package com.intellij.psi;

import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class WrappedElementAnchor extends PsiAnchor {
  private final SmartPointerAnchorProvider myAnchorProvider;
  private final PsiAnchor myBaseAnchor;

  public WrappedElementAnchor(@NotNull SmartPointerAnchorProvider provider, @NotNull PsiAnchor anchor) {
    myAnchorProvider = provider;
    myBaseAnchor = anchor;
  }

  @Nullable
  @Override
  public PsiElement retrieve() {
    PsiElement baseElement = myBaseAnchor.retrieve();
    return baseElement == null ? null : myAnchorProvider.restoreElement(baseElement);
  }

  @Override
  public PsiFile getFile() {
    PsiElement element = retrieve();
    return element == null ? null : element.getContainingFile();
  }

  @Override
  public int getStartOffset() {
    PsiElement element = retrieve();
    return element == null || element.getTextRange() == null ? -1 : element.getTextRange().getStartOffset();
  }

  @Override
  public int getEndOffset() {
    PsiElement element = retrieve();
    return element == null || element.getTextRange() == null ? -1 : element.getTextRange().getEndOffset();
  }

  @Override
  public String toString() {
    return "WrappedElementAnchor(" + myBaseAnchor + "; provider=" + myAnchorProvider + ")";
  }
}

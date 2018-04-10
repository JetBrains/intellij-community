/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A superclass for all element types for root AST nodes in a {@link com.intellij.psi.PsiFile}.
 */
public class IFileElementType extends ILazyParseableElementType {
  public IFileElementType(@Nullable final Language language) {
    super("FILE", language);
  }

  public IFileElementType(@NonNls @NotNull final String debugName, @Nullable final Language language) {
    super(debugName, language);
  }

  /**
   * Allows to construct file element types without registering them, as in {@link IElementType#IElementType(String, Language, boolean)}.
   */
  public IFileElementType(@NonNls @NotNull final String debugName, @Nullable final Language language, boolean register) {
    super(debugName, language, register);
  }

  @Nullable
  @Override
  public ASTNode parseContents(@NotNull final ASTNode chameleon) {
    final PsiElement psi = chameleon.getPsi();
    assert psi != null : "Bad chameleon: " + chameleon;
    return doParseContents(chameleon, psi);
  }
}

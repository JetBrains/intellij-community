/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NamedFoldingDescriptor extends FoldingDescriptor {
  private final String myPlaceholderText;

  public NamedFoldingDescriptor(@NotNull PsiElement e, int start, int end, @Nullable FoldingGroup group, @NotNull String placeholderText) {
    this(e.getNode(), new TextRange(start, end), group, placeholderText);
  }

  public NamedFoldingDescriptor(@NotNull ASTNode node, int start, int end, @Nullable FoldingGroup group, @NotNull String placeholderText) {
    this(node, new TextRange(start, end), group, placeholderText);
  }

  public NamedFoldingDescriptor(@NotNull ASTNode node,
                         @NotNull final TextRange range,
                         @Nullable FoldingGroup group,
                         @NotNull String placeholderText) {
    super(node, range, group);
    myPlaceholderText = placeholderText;
  }

  @Override
  @NotNull 
  public String getPlaceholderText() {
    return myPlaceholderText;
  }
}

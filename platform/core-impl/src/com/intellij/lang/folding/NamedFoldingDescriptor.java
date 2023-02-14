/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @deprecated Use {@link FoldingDescriptor} instead.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public final class NamedFoldingDescriptor extends FoldingDescriptor {
  /**
   * @deprecated Use {@link FoldingDescriptor#FoldingDescriptor(PsiElement, int, int, FoldingGroup, String)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public NamedFoldingDescriptor(@NotNull PsiElement e, int start, int end, @Nullable FoldingGroup group, @NotNull String placeholderText) {
    super(e, start, end, group, placeholderText);
  }

  /**
   * @deprecated Use {@link FoldingDescriptor#FoldingDescriptor(ASTNode, TextRange, FoldingGroup, String)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public NamedFoldingDescriptor(@NotNull ASTNode node,
                         @NotNull final TextRange range,
                         @Nullable FoldingGroup group,
                         @NotNull String placeholderText) {
    super(node, range, group, placeholderText);
  }

  /**
   * @deprecated Use {@link FoldingDescriptor#FoldingDescriptor(ASTNode, TextRange, FoldingGroup, String, Boolean, Set)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public NamedFoldingDescriptor(@NotNull ASTNode node,
                                @NotNull final TextRange range,
                                @Nullable FoldingGroup group,
                                @NotNull String placeholderText,
                                @Nullable("null means unknown") Boolean collapsedByDefault,
                                @NotNull Set<Object> dependencies) {
    super(node, range, group, placeholderText, collapsedByDefault, dependencies);
  }
}

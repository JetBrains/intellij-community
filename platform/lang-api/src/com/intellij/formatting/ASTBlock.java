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

package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface ASTBlock extends Block {
  @Nullable
  ASTNode getNode();

  /**
   * @return {@link ASTNode} from this {@code block} if it's {@link ASTBlock}, null otherwise
   */
  @Contract("null -> null")
  @Nullable
  static ASTNode getNode(@Nullable Block block) {
    return ObjectUtils.doIfCast(block, ASTBlock.class, it -> it.getNode());
  }

  /**
   * @return element type of the {@link ASTNode} contained in the {@code block}, if it's an {@link ASTBlock}, null otherwise
   */
  @Contract("null -> null")
  @Nullable
  static IElementType getElementType(@Nullable Block block) {
    return ObjectUtils.doIfNotNull(getNode(block), PsiUtilCore::getElementType);
  }

  /**
   * @return {@link PsiElement} from {@link ASTNode} from this {@code block} if it's {@link ASTBlock}, null otherwise
   */
  @Contract("null -> null")
  @Nullable
  static PsiElement getPsiElement(@Nullable Block block) {
    return ObjectUtils.doIfNotNull(getNode(block), it -> it.getPsi());
  }
}

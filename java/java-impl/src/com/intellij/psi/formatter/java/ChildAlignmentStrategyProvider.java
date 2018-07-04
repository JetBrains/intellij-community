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
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ChildAlignmentStrategyProvider {

  public abstract AlignmentStrategy getNextChildStrategy(@NotNull ASTNode child);

  public static final ChildAlignmentStrategyProvider NULL_STRATEGY_PROVIDER = new ChildAlignmentStrategyProvider() {
    @Override
    public AlignmentStrategy getNextChildStrategy(@NotNull ASTNode child) {
      return AlignmentStrategy.getNullStrategy();
    }
  };
  
  public static boolean isWhiteSpaceWithBlankLines(@Nullable ASTNode node) {
    return node != null && node.getElementType() == TokenType.WHITE_SPACE && StringUtil.countNewLines(node.getChars()) > 1;
  }
}

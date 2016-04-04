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
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class SubsequentVariablesAligner extends ChildAlignmentStrategyProvider {

  private final static Set<IElementType> TYPES_TO_ALIGN = ContainerUtil.newHashSet(
    JavaTokenType.IDENTIFIER,
    JavaTokenType.EQ
  );

  private AlignmentStrategy myAlignmentStrategy;

  public SubsequentVariablesAligner() {
    updateAlignmentStrategy();
  }

  private void updateAlignmentStrategy() {
    myAlignmentStrategy = AlignmentStrategy.createAlignmentPerTypeStrategy(TYPES_TO_ALIGN, JavaElementType.LOCAL_VARIABLE, true);
  }

  @Override
  public AlignmentStrategy getNextChildStrategy(@NotNull ASTNode child) {
    IElementType childType = child.getElementType();
    if (childType != JavaElementType.DECLARATION_STATEMENT || StringUtil.countNewLines(child.getChars()) > 0) {
      updateAlignmentStrategy();
      return AlignmentStrategy.getNullStrategy();
    }

    if (isWhiteSpaceWithBlankLines(child.getTreePrev())) {
      updateAlignmentStrategy();
      return myAlignmentStrategy;
    }

    return myAlignmentStrategy;
  }
  
}
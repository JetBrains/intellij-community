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

import java.util.Set;

public class SubsequentVariablesAlignerConfigurations {

  private final static Set<IElementType> LOCAL_VAR_TYPES_TO_ALIGN = ContainerUtil.newHashSet(
    JavaTokenType.IDENTIFIER,
    JavaTokenType.EQ
  );

  private final static Set<IElementType> ASSIGN_TYPES_TO_ALIGN = ContainerUtil.newHashSet(
    JavaElementType.REFERENCE_EXPRESSION,
    JavaTokenType.EQ
  );

  static final CompositeAligner.AlignerConfiguration subsequentVariableAligner = new SubsequentVariableAligner();
  static final CompositeAligner.AlignerConfiguration subsequentAssignmentAligner = new SubsequentAssignmentAligner();

  private static class SubsequentVariableAligner implements CompositeAligner.AlignerConfiguration {
    @Override
    public boolean shouldAlign(@NotNull ASTNode child) {
      return child.getElementType() == JavaElementType.DECLARATION_STATEMENT && StringUtil.countNewLines(child.getChars()) == 0;
    }

    @NotNull
    @Override
    public AlignmentStrategy createStrategy() {
      return AlignmentStrategy.createAlignmentPerTypeStrategy(LOCAL_VAR_TYPES_TO_ALIGN, JavaElementType.LOCAL_VARIABLE, true);
    }
  }

  private static class SubsequentAssignmentAligner implements CompositeAligner.AlignerConfiguration {
    @Override
    public boolean shouldAlign(@NotNull ASTNode child) {
      IElementType childType = child.getElementType();
      if (childType == JavaElementType.EXPRESSION_STATEMENT) {
        ASTNode expressionNode = child.getFirstChildNode();
        if (expressionNode != null && expressionNode.getElementType() == JavaElementType.ASSIGNMENT_EXPRESSION) {
          return true;
        }
      }
      return false;
    }

    @NotNull
    @Override
    public AlignmentStrategy createStrategy() {
      return AlignmentStrategy.createAlignmentPerTypeStrategy(ASSIGN_TYPES_TO_ALIGN, JavaElementType.ASSIGNMENT_EXPRESSION, true);
    }
  }
}
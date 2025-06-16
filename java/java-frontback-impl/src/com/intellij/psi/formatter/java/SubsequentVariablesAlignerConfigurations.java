// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class SubsequentVariablesAlignerConfigurations {

  private static final Set<IElementType> LOCAL_VAR_TYPES_TO_ALIGN = ContainerUtil.newHashSet(
    JavaTokenType.IDENTIFIER,
    JavaTokenType.EQ
  );

  private static final Set<IElementType> ASSIGN_TYPES_TO_ALIGN = ContainerUtil.newHashSet(
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

    @Override
    public @NotNull AlignmentStrategy createStrategy() {
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

    @Override
    public @NotNull AlignmentStrategy createStrategy() {
      return AlignmentStrategy.createAlignmentPerTypeStrategy(ASSIGN_TYPES_TO_ALIGN, JavaElementType.ASSIGNMENT_EXPRESSION, true);
    }
  }
}
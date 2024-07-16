// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentInColumnsConfig;
import com.intellij.formatting.alignment.AlignmentInColumnsHelper;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaJspElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class SubsequentFieldAligner extends ChildAlignmentStrategyProvider {

  private static final Set<IElementType> TYPES_TO_ALIGN = ContainerUtil.newHashSet(
    JavaElementType.MODIFIER_LIST,
    JavaElementType.TYPE,
    JavaTokenType.IDENTIFIER,
    JavaTokenType.EQ
  );

  private static final AlignmentInColumnsConfig ALIGNMENT_IN_COLUMNS_CONFIG = new AlignmentInColumnsConfig(
    TokenSet.create(JavaTokenType.IDENTIFIER),
    JavaJspElementType.WHITE_SPACE_BIT_SET,
    ElementType.JAVA_COMMENT_BIT_SET,
    TokenSet.EMPTY,
    TokenSet.create(JavaElementType.FIELD)
  );

  private final CommonCodeStyleSettings mySettings;
  private final AlignmentInColumnsHelper myAlignmentInColumnsHelper;
  private AlignmentStrategy myAlignmentStrategy;

  public SubsequentFieldAligner(@NotNull CommonCodeStyleSettings settings) {
    myAlignmentInColumnsHelper = AlignmentInColumnsHelper.INSTANCE;
    myAlignmentStrategy = newAlignmentStrategy();
    mySettings = settings;
  }

  private static AlignmentStrategy newAlignmentStrategy() {
    return AlignmentStrategy.createAlignmentPerTypeStrategy(TYPES_TO_ALIGN, JavaElementType.FIELD, true);
  }

  @Override
  public AlignmentStrategy getNextChildStrategy(@NotNull ASTNode child) {
    if (!ElementType.JAVA_COMMENT_BIT_SET.contains(child.getElementType()) && !shouldUseVarDeclarationAlignment(child)) {
      myAlignmentStrategy = newAlignmentStrategy();
    }

    return JavaElementType.FIELD == child.getElementType()
           ? myAlignmentStrategy
           : AlignmentStrategy.getNullStrategy();
  }

  protected boolean shouldUseVarDeclarationAlignment(@NotNull ASTNode node) {
    return JavaElementType.FIELD == node.getElementType()
           && (!myAlignmentInColumnsHelper.useDifferentVarDeclarationAlignment(node, ALIGNMENT_IN_COLUMNS_CONFIG, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS)
               || MultipleFieldDeclarationHelper.compoundFieldPart(node));
  }

}
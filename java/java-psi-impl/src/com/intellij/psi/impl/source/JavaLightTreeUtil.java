// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IKeywordElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

/**
 * @author peter
 */
public final class JavaLightTreeUtil {
  @Nullable
  @Contract("_,null->null")
  public static List<LighterASTNode> getArgList(@NotNull LighterAST tree, @Nullable LighterASTNode call) {
    LighterASTNode anonClass = LightTreeUtil.firstChildOfType(tree, call, ANONYMOUS_CLASS);
    LighterASTNode exprList = LightTreeUtil.firstChildOfType(tree, anonClass != null ? anonClass : call, EXPRESSION_LIST);
    return exprList == null ? null : getExpressionChildren(tree, exprList);
  }

  @Nullable
  @Contract("_,null->null")
  public static String getNameIdentifierText(@NotNull LighterAST tree, @Nullable LighterASTNode idOwner) {
    LighterASTNode id = LightTreeUtil.firstChildOfType(tree, idOwner, JavaTokenType.IDENTIFIER);
    return id != null ? RecordUtil.intern(tree.getCharTable(), id) : null;
  }

  @NotNull
  public static List<LighterASTNode> getExpressionChildren(@NotNull LighterAST tree, @NotNull LighterASTNode node) {
    return LightTreeUtil.getChildrenOfType(tree, node, ElementType.EXPRESSION_BIT_SET);
  }

  @Nullable
  public static LighterASTNode findExpressionChild(@NotNull LighterAST tree, @Nullable LighterASTNode node) {
    return LightTreeUtil.firstChildOfType(tree, node, ElementType.EXPRESSION_BIT_SET);
  }

  @Nullable
  public static LighterASTNode skipParenthesesCastsDown(@NotNull LighterAST tree, @Nullable LighterASTNode node) {
    while (node != null) {
      IElementType type = node.getTokenType();
      if (type != PARENTH_EXPRESSION && type != TYPE_CAST_EXPRESSION) break;
      if (type == TYPE_CAST_EXPRESSION && isPrimitiveCast(tree, node)) break;
      node = findExpressionChild(tree, node);
    }
    return node;
  }

  public static boolean isPrimitiveCast(@NotNull LighterAST tree, @NotNull LighterASTNode node) {
    LighterASTNode typeElement = LightTreeUtil.firstChildOfType(tree, node, TYPE);
    if (typeElement != null) {
      LighterASTNode item = ContainerUtil.getOnlyItem(tree.getChildren(typeElement));
      return item != null && item.getTokenType() instanceof IKeywordElementType;
    }
    return false;
  }

  @Nullable
  public static LighterASTNode skipParenthesesDown(@NotNull LighterAST tree, @Nullable LighterASTNode expression) {
    while (expression != null && expression.getTokenType() == PARENTH_EXPRESSION) {
      expression = findExpressionChild(tree, expression);
    }
    return expression;
  }

  /**
   * Returns true if given element (which is modifier list owner) has given explicit modifier
   *
   * @param tree an AST tree
   * @param modifierListOwner element to check modifier of
   * @param modifierKeyword modifier to look for (e.g. {@link JavaTokenType#VOLATILE_KEYWORD}
   * @return true if given element has given explicit modifier
   */
  public static boolean hasExplicitModifier(@NotNull LighterAST tree,
                                            @Nullable LighterASTNode modifierListOwner,
                                            @NotNull IElementType modifierKeyword) {
    LighterASTNode modifierList = LightTreeUtil.firstChildOfType(tree, modifierListOwner, MODIFIER_LIST);
    return LightTreeUtil.firstChildOfType(tree, modifierList, modifierKeyword) != null;
  }
}

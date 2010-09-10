/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.formatting.alignment;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * This class provides helper methods to use for <code>'align in columns'</code> processing.
 * <p/>
 * <code>'Align in columns'</code> here means format the code like below:
 * <pre>
 *     class Test {
 *         private int    iii = 1;
 *         private double d   = 2;
 *     }
 * </pre>
 * I.e. components of two lines are aligned to each other in columns.
 * <p/>
 * This class is not singleton but it's thread-safe and provides single-point-of-usage field {@link #INSTANCE}.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since May 24, 2010 3:09:52 PM
 */
public class AlignmentInColumnsHelper {

  /** Single-point-of-usage field. */
  public static final AlignmentInColumnsHelper INSTANCE = new AlignmentInColumnsHelper();

  /**
   * Allows to answer if given node should be aligned to the previous node of the same type according to the given alignment config
   * assuming that given node is a variable declaration.
   *
   * @param node      target node which alignment strategy is to be defined
   * @param config    alignment config to use for processing
   * @return          <code>true</code> if given node should be aligned to the previous one; <code>false</code> otherwise
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  public boolean useDifferentVarDeclarationAlignment(ASTNode node, AlignmentInColumnsConfig config) {
    ASTNode prev = getPreviousAdjacentNodeOfTargetType(node, config);
    if (prev == null) {
      return true;
    }

    ASTNode curr = deriveNodeOfTargetType(node, Collections.singleton(prev.getElementType()));
    if (curr == null) {
      return true;
    }

    // The main idea is to avoid alignment like the one below:
    //     private final int    i;
    //                   double d;
    // I.e. we want to avoid alignment-implied long indents from the start of line.
    // Please note that we do allow alignment like below:
    //     private final int    i;
    //     private       double d;
    ASTNode prevSubNode = getSubNodeThatStartsNewLine(prev.getFirstChildNode(), config);
    ASTNode currSubNode = getSubNodeThatStartsNewLine(curr.getFirstChildNode(), config);
    while (true) {
      boolean prevNodeIsDefined = prevSubNode != null;
      boolean currNodeIsDefined = currSubNode != null;
      // Check if one sub-node starts from new line and another one doesn't start.
      if (prevNodeIsDefined ^ currNodeIsDefined) {
        return true;
      }
      if (prevSubNode == null) {
        break;
      }
      if (prevSubNode.getElementType() != currSubNode.getElementType()
          || StringUtil.countNewLines(prevSubNode.getChars()) != StringUtil.countNewLines(currSubNode.getChars()))
      {
        return true;
      }
      prevSubNode = getSubNodeThatStartsNewLine(prevSubNode.getTreeNext(), config);
      currSubNode = getSubNodeThatStartsNewLine(currSubNode.getTreeNext(), config);
    }

    // There is a possible declaration like the one below
    //     int i1 = 1;
    //     int i2, i3 = 2;
    // Three fields are declared here - 'i1', 'i2' and 'i3'. So, the check if field 'i2' contains assignment should be
    // performed against 'i3'.
    ASTNode currentFieldToUse = curr;
    ASTNode nextNode = curr.getTreeNext();
    for (; nextNode != null && nextNode.getTreeParent() == curr.getTreeParent(); nextNode = nextNode.getTreeNext()) {
      IElementType type = nextNode.getElementType();
      if (config.getWhiteSpaceTokenTypes().contains(type)) {
        ASTNode previous = nextNode.getTreePrev();
        if ((previous != null && previous.getElementType() == curr.getElementType()) || StringUtil.countNewLines(nextNode.getChars()) > 1) {
          break;
        }
        continue;
      }

      if (config.getCommentTokenTypes().contains(type)) {
        continue;
      }

      if (type == curr.getElementType()) {
        currentFieldToUse = nextNode;
      }
    }

    boolean prevContainsDefinition = containsSubNodeOfType(prev, config.getEqualType());
    boolean currentContainsDefinition = containsSubNodeOfType(currentFieldToUse, config.getEqualType());

    return prevContainsDefinition ^ currentContainsDefinition;
  }

  /**
   * Tries to find previous node adjacent to the given node that has the same
   * {@link AlignmentInColumnsConfig#getTargetDeclarationTypes() target type}.
   *
   * @param baseNode    base node to use
   * @param config      current processing config
   * @return            previous node to the given base node that has that same type and is adjacent to it if possible;
   *                    <code>null</code> otherwise
   */
  @SuppressWarnings({"StatementWithEmptyBody"})
  @Nullable
  private static ASTNode getPreviousAdjacentNodeOfTargetType(ASTNode baseNode, AlignmentInColumnsConfig config) {
    ASTNode nodeOfTargetType = deriveNodeOfTargetType(baseNode, config.getTargetDeclarationTypes());
    if (nodeOfTargetType == null) {
      return null;
    }

    ASTNode prev = getPreviousNode(baseNode);

    // Find non-comment and non-white space previous node.
    for (; prev != null; prev = getPreviousNode(prev, nodeOfTargetType.getElementType())) {
      IElementType type = prev.getElementType();
      if (config.getCommentTokenTypes().contains(type)) {
        continue;
      }

      if (config.getWhiteSpaceTokenTypes().contains(type)) {
        if (StringUtil.countChars(prev.getText(), '\n') > 1) {
          return null;
        }
        continue;
      }
      break;
    }

    if (prev == null) {
      return null;
    }

    return deriveNodeOfTargetType(prev, Collections.singleton(nodeOfTargetType.getElementType()));
  }

  /**
   * There is a possible case that given base node doesn't have {@link AlignmentInColumnsConfig#getTargetDeclarationTypes() target type}
   * but its first child node or first child node of the first child node etc does.
   * <p/>
   * This method tries to derive node of the target type from the given node.
   *
   * @param baseNode          base node to process
   * @param targetTypes       target node types
   * @return                  base node or its first descendant child that has
   *                          {@link AlignmentInColumnsConfig#getTargetDeclarationTypes() target type} target type if the one if found;
   *                          <code>null</code> otherwise
   */
  @Nullable
  private static ASTNode deriveNodeOfTargetType(ASTNode baseNode, Set<IElementType> targetTypes) {
    if (targetTypes.contains(baseNode.getElementType())) {
      return baseNode;
    }
    for (ASTNode node = baseNode; node != null; node = node.getFirstChildNode()) {
      IElementType nodeType = node.getElementType();
      if (targetTypes.contains(nodeType)) {
        return node;
      }
    }
    return null;
  }

  /**
   * Shorthand for calling {@link #getPreviousNode(ASTNode)} with the type of the given node as a target type.
   *
   * @param startNode   start node to use
   * @return            direct or indirect previous node of the same type of the given one if possible; <code>null</code> otherwise
   */
  @Nullable
  private static ASTNode getPreviousNode(ASTNode startNode) {
    return getPreviousNode(startNode, startNode.getElementType());
  }

  /**
   * Tries to find node that is direct or indirect previous node of the given node.
   * <p/>
   * E.g. there is a possible use-case:
   * <pre>
   *                     n1
   *                  /    \
   *                n21    n22
   *                 |      |
   *                n31    n32
   * </pre>
   * Let's assume that target node is <code>'n32'</code>. 'n31' is assumed to be returned from this method then.
   * <p/>
   * <b>Note:</b> current method avoids going too deep if found node type is the same as start node type
   *
   * @param startNode   start node to use
   * @return            direct or indirect previous node of the given one having target type if possible; <code>null</code> otherwise
   */
  @Nullable
  private static ASTNode getPreviousNode(ASTNode startNode, IElementType targetType) {
    // Try to find node that is direct previous node of the target node or is parent of the node that is indirect previous node
    // of the target node.
    ASTNode prev = startNode.getTreePrev();
    for (ASTNode node = startNode; prev == null && node != null; node = node.getTreeParent()) {
      prev = node.getTreePrev();
    }

    ASTNode result = prev;

    // Find the rightest child of the target previous parent node if necessary.
    if (prev != null) {
      for (; prev != null && prev.getElementType() != targetType; prev = prev.getLastChildNode()) {
        result = prev;
      }
    }

    return result;
  }

  @SuppressWarnings({"StatementWithEmptyBody"})
  @Nullable
  private static ASTNode getSubNodeThatStartsNewLine(@Nullable ASTNode startNode, AlignmentInColumnsConfig config) {
    if (startNode == null) {
      return null;
    }

    ASTNode parent = startNode.getTreeParent();
    if (parent == null) {
      // Never expect to be here.
      return null;
    }

    // Check if previous node to the start node is white space that contains line feeds.
    boolean returnFirstNonEmptySubNode = false;

    //// Try to find node that is direct previous node of the target node or is parent of the node that is indirect previous node
    //// of the target node.
    ASTNode prev = getPreviousNode(startNode);

    if (prev != null && config.getWhiteSpaceTokenTypes().contains(prev.getElementType()) && StringUtil.countNewLines(prev.getChars()) > 0) {
      returnFirstNonEmptySubNode = true;
    }

    boolean stop = false;
    for (ASTNode result = startNode; result != null && result.getTreeParent() == parent; result = result.getTreeNext()) {
      if (config.getStopMultilineCheckElementTypes().contains(result.getElementType())) {
        return null;
      }
      if (result.getTextLength() <= 0) {
        continue;
      }
      if (config.getCommentTokenTypes().contains(result.getElementType())) {
        continue;
      }
      if (config.getWhiteSpaceTokenTypes().contains(result.getElementType()) && StringUtil.countNewLines(result.getChars()) > 0) {
        stop = true;
        continue;
      }
      if (returnFirstNonEmptySubNode) {
        return result;
      }
      if (stop) {
        return result;
      }
    }

    return null;
  }

  /**
   * Allows to check if given node contains direct child (level one depth) of the given type.
   *
   * @param node    parent node to check
   * @param type    target child node type
   * @return        <code>true</code> if given parent node contains direct child of the given type; <code>false</code> otherwise
   */
  private static boolean containsSubNodeOfType(ASTNode node, IElementType type) {
    for(ASTNode child = node.getFirstChildNode(); child != null && child.getTreeParent() == node; child = child.getTreeNext()) {
      if (child.getElementType() == type) {
        return true;
      }
    }
    return false;
  }
}

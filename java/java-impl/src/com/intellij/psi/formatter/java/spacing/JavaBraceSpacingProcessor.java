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
package com.intellij.psi.formatter.java.spacing;

import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.java.FormattingAstUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;

/**
 * Utility class that encapsulates algorithm of defining {@link Spacing} to use for the target code block containing brace.
 * <p/>
 * This class is not singleton but it's thread-safe and provides single-point-of-usage field {@link #INSTANCE}.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Apr 26, 2010 1:52:52 PM
 */
public class JavaBraceSpacingProcessor {

  /** Single-point-of-usage field. */
  public static final JavaBraceSpacingProcessor INSTANCE = new JavaBraceSpacingProcessor();

  /**
   * Allows to get {@link Spacing} to use for the given AST node that is assumed to be right sibling of left curly brace and that brace.
   *
   * @param settings      code formatting settings to use
   * @param rightNode     right sibling of target left curly brace
   * @return              spacing to use for the left curly brace and it's given right sibling
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  public Spacing getLBraceSpacing(CodeStyleSettings settings, ASTNode rightNode) {
    if (shouldUseFlyingGeeseStyleForLeftBrace(settings, rightNode)) {
      return getFlyingGeesSpacing(settings);
    }
    return Spacing.createSpacing(
      0, 0, settings.BLANK_LINES_AFTER_CLASS_HEADER + 1, settings.KEEP_LINE_BREAKS, settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    );
  }

  /**
   * Allows to get {@link Spacing} to use for the given AST node that is assumed to be left sibling of right curly brace and that brace.
   *
   * @param settings      code formatting settings to use
   * @param leftNode      left sibling of target right curly brace
   * @return              spacing to use for the right curly brace and it's given left sibling
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  public Spacing getRBraceSpacing(CodeStyleSettings settings, ASTNode leftNode) {
    if (shouldUseFlyingGeeseStyleForRightBrace(settings, leftNode)) {
      return getFlyingGeesSpacing(settings);
    }
    int minLineFeeds = getMinLineFeedsBetweenRBraces(leftNode);
    return Spacing.createSpacing(0, Integer.MAX_VALUE, minLineFeeds, settings.KEEP_LINE_BREAKS, settings.KEEP_BLANK_LINES_BEFORE_RBRACE);
  }

  private static boolean shouldUseFlyingGeeseStyleForLeftBrace(CodeStyleSettings settings, ASTNode rightNode) {
    if (!settings.USE_FLYING_GEESE_BRACES) {
      return false;
    }

    ASTNode rightSibling = FormattingAstUtil.getNextNonWhiteSpaceNode(rightNode);
    return rightSibling != null && rightSibling.getElementType() == JavaTokenType.RBRACE;
  }

  private static boolean shouldUseFlyingGeeseStyleForRightBrace(CodeStyleSettings settings, ASTNode leftNode) {
    if (!settings.USE_FLYING_GEESE_BRACES) {
      return false;
    }

    ASTNode leftSibling = FormattingAstUtil.getPrevNonWhiteSpaceNode(leftNode);
    return leftSibling != null && leftSibling.getElementType() == JavaTokenType.LBRACE;
  }

  private static Spacing getFlyingGeesSpacing(CodeStyleSettings settings) {
    return Spacing.createSpacing(settings.FLYING_GEESE_BRACES_GAP, settings.FLYING_GEESE_BRACES_GAP, 0, false, 0);
  }

  /**
   * Allows to calculate <code>'min line feed'</code> setting of the {@link Spacing} to be used between two closing braces
   * (assuming that left AST node that ends with closing brace is given to this method).
   *
   * @param leftNode    left AST node that ends with closing brace
   * @return            <code>'min line feed'</code> setting of {@link Spacing} object to use for the given AST node and
   *                    closing brace
   */
  private static int getMinLineFeedsBetweenRBraces(ASTNode leftNode) {

    // The general idea is to return zero in situation when opening curly braces goes one after other, e.g.
    //     new Expectations() {{
    //         foo();}}
    // We don't want line feed between closing curly braces here.

    if (leftNode == null || leftNode.getElementType() != JavaElementType.CLASS_INITIALIZER) {
      return 1;
    }

    ASTNode lbraceCandidate = leftNode.getTreePrev();
    return (lbraceCandidate != null && lbraceCandidate.getElementType() == JavaTokenType.LBRACE) ? 0 : 1;
  }
}

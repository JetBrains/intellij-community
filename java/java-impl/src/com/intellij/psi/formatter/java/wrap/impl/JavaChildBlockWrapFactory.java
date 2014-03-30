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
package com.intellij.psi.formatter.java.wrap.impl;

import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.java.JavaFormatterUtil;
import com.intellij.psi.formatter.java.wrap.ReservedWrapsProvider;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates algorithm of construction {@link Wrap wraps} for sub-blocks of particular {@link ASTBlock block} taking into
 * consideration current formatting settings.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Apr 21, 2010 4:49:49 PM
 */
public class JavaChildBlockWrapFactory {

  /**
   * Creates {@link Wrap wrap} to be used with the children blocks of the the given block.
   *
   * @param block                   target block which sub-blocks should use wrap created by the current method
   * @param settings                code formatting settings to consider during wrap construction
   * @param reservedWrapsProvider   reserved {@code 'element type -> wrap instance'} mappings provider. <b>Note:</b> this
   *                                argument is considered to be a part of legacy heritage and is intended to be removed as
   *                                soon as formatting code refactoring is done
   * @return                        wrap to use for the sub-blocks of the given block
   */
  @Nullable
  public Wrap create(ASTBlock block, CommonCodeStyleSettings settings, ReservedWrapsProvider reservedWrapsProvider) {
    ASTNode node = block.getNode();
    Wrap wrap = block.getWrap();
    final IElementType nodeType = node.getElementType();
    if (nodeType == JavaElementType.EXTENDS_LIST || nodeType == JavaElementType.IMPLEMENTS_LIST) {
      return Wrap.createWrap(settings.EXTENDS_LIST_WRAP, false);
    }
    else if (node instanceof PsiPolyadicExpression) {
      Wrap actualWrap = wrap != null ? wrap : reservedWrapsProvider.getReservedWrap(JavaElementType.BINARY_EXPRESSION);
      if (actualWrap == null) {
        return Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false);
      }
      else {
        if (JavaFormatterUtil.areSamePriorityBinaryExpressions(node, node.getTreeParent())) {
          return actualWrap;
        }
        else {
          return Wrap.createChildWrap(actualWrap, WrapType.byLegacyRepresentation(settings.BINARY_OPERATION_WRAP), false);
        }
      }
    }
    else if (nodeType == JavaElementType.CONDITIONAL_EXPRESSION) {
      return Wrap.createWrap(settings.TERNARY_OPERATION_WRAP, false);
    }
    else if (nodeType == JavaElementType.ASSERT_STATEMENT) {
      return Wrap.createWrap(settings.ASSERT_STATEMENT_WRAP, false);
    }
    else if (nodeType == JavaElementType.FOR_STATEMENT) {
      return Wrap.createWrap(settings.FOR_STATEMENT_WRAP, false);
    }
    else if (nodeType == JavaElementType.THROWS_LIST) {
      return Wrap.createWrap(settings.THROWS_LIST_WRAP, true);
    }
    else if (nodeType == JavaElementType.CODE_BLOCK) {
      if (settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE && node.getPsi().getParent() instanceof PsiMethod && !node.textContains('\n')) {
        return null;
      }
      return Wrap.createWrap(WrapType.NORMAL, false);
    }
    else if (JavaFormatterUtil.isAssignment(node)) {
      return Wrap.createWrap(settings.ASSIGNMENT_WRAP, true);
    }
    else {
      return null;
    }
  }
}

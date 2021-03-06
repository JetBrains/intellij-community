/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.RegExpElementTypes;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RegExpGroupImpl extends RegExpElementImpl implements RegExpGroup {
  public RegExpGroupImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public void accept(RegExpElementVisitor visitor) {
    visitor.visitRegExpGroup(this);
  }

  @Override
  public @NotNull RegExpPattern getPattern() {
    final ASTNode node = getNode().findChildByType(RegExpElementTypes.PATTERN);
    assert node != null;
    return (RegExpPattern)node.getPsi();
  }

  @Override
  public boolean isCapturing() {
    final Type type = getType();
    return type == Type.CAPTURING_GROUP || type == Type.NAMED_GROUP || type == Type.QUOTED_NAMED_GROUP || type == Type.PYTHON_NAMED_GROUP;
  }

  @Override
  public boolean isAnyNamedGroup() {
    final Type type = getType();
    return type == Type.NAMED_GROUP || type == Type.QUOTED_NAMED_GROUP || type == Type.PYTHON_NAMED_GROUP;
  }

  @Override
  public Type getType() {
    final IElementType elementType = getNode().getFirstChildNode().getElementType();
    if (elementType == RegExpTT.GROUP_BEGIN) {
      return Type.CAPTURING_GROUP;
    }
    else if (elementType == RegExpTT.RUBY_NAMED_GROUP) {
      return Type.NAMED_GROUP;
    }
    else if (elementType == RegExpTT.PYTHON_NAMED_GROUP) {
      return Type.PYTHON_NAMED_GROUP;
    }
    else if (elementType == RegExpTT.RUBY_QUOTED_NAMED_GROUP) {
      return Type.QUOTED_NAMED_GROUP;
    }
    else if (elementType == RegExpTT.ATOMIC_GROUP) {
      return Type.ATOMIC;
    }
    else if (elementType == RegExpTT.NON_CAPT_GROUP) {
      return Type.NON_CAPTURING;
    }
    else if (elementType == RegExpTT.SET_OPTIONS) {
      return Type.OPTIONS;
    }
    else if (elementType == RegExpTT.POS_LOOKAHEAD) {
      return Type.POSITIVE_LOOKAHEAD;
    }
    else if (elementType == RegExpTT.NEG_LOOKAHEAD) {
      return Type.NEGATIVE_LOOKAHEAD;
    }
    else if (elementType == RegExpTT.POS_LOOKBEHIND) {
      return Type.POSITIVE_LOOKBEHIND;
    }
    else if (elementType == RegExpTT.NEG_LOOKBEHIND) {
      return Type.NEGATIVE_LOOKBEHIND;
    }
    else if (elementType == RegExpTT.PCRE_BRANCH_RESET) {
      return Type.PCRE_BRANCH_RESET;
    }
    throw new AssertionError();
  }

  @Override
  public String getGroupName() {
    final ASTNode nameNode = getNode().findChildByType(RegExpTT.NAME);
    return nameNode != null ? nameNode.getText() : null;
  }

  @Override
  public String getName() {
    return getGroupName();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public int getTextOffset() {
    return getFirstChild().getNextSibling().getTextOffset();
  }
}

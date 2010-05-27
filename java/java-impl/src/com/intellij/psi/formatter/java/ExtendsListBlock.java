/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.ElementType;

import java.util.ArrayList;
import java.util.List;

public class ExtendsListBlock extends AbstractJavaBlock{
  public ExtendsListBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, CodeStyleSettings settings) {
    super(node, wrap, alignment, Indent.getNoneIndent(), settings);
  }

  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();
    ArrayList<Block> elementsExceptKeyword = new ArrayList<Block>();
    myChildAlignment = createChildAlignment();
    myChildIndent = Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS);
    myUseChildAttributes = true;
    Wrap childWrap = createChildWrap();
    ASTNode child = myNode.getFirstChildNode();

    Alignment alignment = alignList() ? Alignment.createAlignment() : null;

    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        if (ElementType.KEYWORD_BIT_SET.contains(child.getElementType())) {
          if (!elementsExceptKeyword.isEmpty()) {
            result.add(new SyntheticCodeBlock(elementsExceptKeyword, null,  mySettings, Indent.getNoneIndent(), null));
            elementsExceptKeyword = new ArrayList<Block>();
          }
          result.add(createJavaBlock(child, mySettings, myChildIndent, arrangeChildWrap(child, childWrap), alignment));
        } else {
          processChild(elementsExceptKeyword, child, myChildAlignment, childWrap, myChildIndent);

        }
      }
      child = child.getTreeNext();
    }
    if (!elementsExceptKeyword.isEmpty()) {
      result.add(new SyntheticCodeBlock(elementsExceptKeyword, alignment,  mySettings, Indent.getNoneIndent(), null));
    }

    return result;

  }

  private boolean alignList() {
    if (myNode.getElementType() == ElementType.EXTENDS_LIST) {
      return mySettings.ALIGN_MULTILINE_EXTENDS_LIST;
    } else if (myNode.getElementType() == ElementType.IMPLEMENTS_LIST) {
      return mySettings.ALIGN_MULTILINE_EXTENDS_LIST;
    } else if (myNode.getElementType() == ElementType.THROWS_LIST) {
      return mySettings.ALIGN_MULTILINE_THROWS_LIST;
    }
    return false;
  }
}

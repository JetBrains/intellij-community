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

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DocCommentBlock extends AbstractJavaBlock{
  public DocCommentBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();

    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      if (child.getElementType() == ElementType.DOC_COMMENT_START) {
        result.add(createJavaBlock(child, mySettings, Indent.getNoneIndent(),
                                   null, null));
      } else if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getText().trim().length() > 0){
        result.add(createJavaBlock(child, mySettings, Indent.getSpaceIndent(1), null, null));
      }
      child = child.getTreeNext();
    }
    return result;

  }

  protected Wrap getReservedWrap(final IElementType elementType) {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap, final IElementType operationType) {
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Indent.getSpaceIndent(1), null);
  }
}

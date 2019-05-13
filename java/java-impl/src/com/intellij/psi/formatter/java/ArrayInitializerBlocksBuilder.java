/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

import static com.intellij.psi.formatter.java.JavaFormatterUtil.getWrapType;

public class ArrayInitializerBlocksBuilder {
  private final ASTNode myNode;
  private final BlockFactory myBlockFactory;
  private final CommonCodeStyleSettings mySettings;

  public ArrayInitializerBlocksBuilder(ASTNode node, BlockFactory blockFactory) {
    myNode = node;
    myBlockFactory = blockFactory;
    mySettings = myBlockFactory.getSettings();
  }

  public List<Block> buildBlocks() {
    Wrap wrap = Wrap.createWrap(getWrapType(mySettings.ARRAY_INITIALIZER_WRAP), false);
    Alignment alignment = mySettings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION ? Alignment.createAlignment() : null;

    ChildrenBlocksBuilder.Config config = new ChildrenBlocksBuilder.Config()
      .setDefaultIndent(Indent.getContinuationWithoutFirstIndent())
      .setIndent(JavaTokenType.RBRACE, Indent.getNoneIndent())
      .setIndent(JavaTokenType.LBRACE, Indent.getNoneIndent())

      .setDefaultWrap(wrap)
      .setNoWrap(JavaTokenType.COMMA)
      .setNoWrap(JavaTokenType.RBRACE)
      .setNoWrap(JavaTokenType.LBRACE)

      .setDefaultAlignment(alignment)
      .setNoAlignment(JavaTokenType.COMMA)
      .setNoAlignment(JavaTokenType.LBRACE)
      .setNoAlignmentIf(JavaTokenType.RBRACE, node -> {
        PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(node.getPsi());
        if (prev == null) return false;
        return prev.getNode().getElementType() != JavaTokenType.COMMA;
      });

    return config.createBuilder().buildNodeChildBlocks(myNode, myBlockFactory);
  }
}

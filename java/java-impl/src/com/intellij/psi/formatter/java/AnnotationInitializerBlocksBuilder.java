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
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

import static com.intellij.psi.formatter.java.JavaFormatterUtil.getWrapType;

public class AnnotationInitializerBlocksBuilder {

  private final BlockFactory myFactory;
  private final ASTNode myNode;
  private final JavaCodeStyleSettings myJavaSettings;

  public AnnotationInitializerBlocksBuilder(ASTNode node, BlockFactory factory) {
    myNode = node;
    myFactory = factory;
    myJavaSettings = myFactory.getJavaSettings();
  }

  public List<Block> buildBlocks() {
    final Wrap wrap = Wrap.createWrap(getWrapType(myJavaSettings.ANNOTATION_PARAMETER_WRAP), false);
    final Alignment alignment = myJavaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS ? Alignment.createAlignment() : null;

    ChildrenBlocksBuilder.Config config = new ChildrenBlocksBuilder.Config()
      .setDefaultIndent(Indent.getContinuationWithoutFirstIndent())
      .setIndent(JavaTokenType.RPARENTH, Indent.getNoneIndent())
      .setIndent(JavaTokenType.LPARENTH, Indent.getNoneIndent())

      .setDefaultWrap(wrap)
      .setNoWrap(JavaTokenType.COMMA)
      .setNoWrap(JavaTokenType.RPARENTH)
      .setNoWrap(JavaTokenType.LPARENTH)

      .setDefaultAlignment(alignment)
      .setNoAlignment(JavaTokenType.COMMA)
      .setNoAlignment(JavaTokenType.LPARENTH)
      .setNoAlignmentIf(JavaTokenType.RPARENTH, node -> {
        PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(node.getPsi());
        if (prev == null) return false;
        return prev instanceof PsiNameValuePair && !PsiTreeUtil.hasErrorElements(prev);
      });

    return config.createBuilder().buildNodeChildBlocks(myNode, myFactory);
  }
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DocCommentBlock extends AbstractJavaBlock{
  public DocCommentBlock(ASTNode node,
                         Wrap wrap,
                         Alignment alignment,
                         Indent indent,
                         CommonCodeStyleSettings settings,
                         JavaCodeStyleSettings javaSettings)
  {
    super(node, wrap, alignment, indent, settings, javaSettings);
  }

  @Override
  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<>();

    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      if (child.getElementType() == JavaDocTokenType.DOC_COMMENT_START) {
        result.add(createJavaBlock(child, mySettings, myJavaSettings, Indent.getNoneIndent(), null, AlignmentStrategy.getNullStrategy()));
      } else if (!FormatterUtil.containsWhiteSpacesOnly(child) && !child.getText().trim().isEmpty()){
        result.add(createJavaBlock(child, mySettings, myJavaSettings, Indent.getSpaceIndent(1), null, AlignmentStrategy.getNullStrategy()));
      }
      child = child.getTreeNext();
    }
    return result;

  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Indent.getSpaceIndent(1), null);
  }
}

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
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LabeledJavaBlock extends AbstractJavaBlock{
  public LabeledJavaBlock(final ASTNode node,
                          final Wrap wrap,
                          final Alignment alignment,
                          final Indent indent,
                          final CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();
    ASTNode child = myNode.getFirstChildNode();
    Indent currentIndent = getLabelIndent();
    Wrap currentWrap = null;
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        result.add(createJavaBlock(child, mySettings, currentIndent, currentWrap, AlignmentStrategy.getNullStrategy()));
        if (child.getElementType() == ElementType.COLON) {
          currentIndent = Indent.getNoneIndent();
          currentWrap =Wrap.createWrap(WrapType.ALWAYS, true);
        }
      }
      child = child.getTreeNext();
    }
    return result;
  }

  private Indent getLabelIndent() {
    if (mySettings.getIndentOptions(StdFileTypes.JAVA).LABEL_INDENT_ABSOLUTE) {
      return Indent.getAbsoluteLabelIndent();
    } else {
      return Indent.getLabelIndent();
    }
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }
}

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
package com.intellij.indentation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * @author oleg
 */
public abstract class IndentationFoldingBuilder implements FoldingBuilder, DumbAware {
  private final TokenSet myTokenSet;

  public IndentationFoldingBuilder(final TokenSet tokenSet) {
    myTokenSet = tokenSet;
  }

  @Override
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode astNode, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new LinkedList<>();
    collectDescriptors(astNode, descriptors);
    return descriptors.toArray(FoldingDescriptor.EMPTY);
  }

  private void collectDescriptors(@NotNull final ASTNode node, @NotNull final List<? super FoldingDescriptor> descriptors) {
    final Queue<ASTNode> toProcess = new LinkedList<>();
    toProcess.add(node);
    while (!toProcess.isEmpty()) {
      final ASTNode current = toProcess.remove();
      if (current.getTreeParent() != null
          && current.getTextLength() > 1
          && myTokenSet.contains(current.getElementType()))
      {
        descriptors.add(new FoldingDescriptor(current, current.getTextRange()));
      }
      for (ASTNode child = current.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        toProcess.add(child);
      }
    }
  }

  @Override
  @Nullable
  public String getPlaceholderText(@NotNull final ASTNode node) {
    final StringBuilder builder = new StringBuilder();
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      String text = child.getText();
      if (text == null) {
        if (builder.length() > 0) {
          break;
        }
      }
      else if (!text.contains("\n")) {
        builder.append(text);
      }
      else if (builder.length() > 0) {
        builder.append(text, 0, text.indexOf('\n'));
        break;
      }
      else {
        builder.append(getFirstNonEmptyLine(text));
        if (builder.length() > 0) {
          break;
        }
      }
      child = child.getTreeNext();
    }
    return builder.toString();
  }

  @NotNull
  private static String getFirstNonEmptyLine(@NotNull final String text) {
    int start = 0;
    int end;
    while ((end = text.indexOf('\n', start)) != -1 && start >= end) {
      start = end + 1;
    }
    return end == -1 ? text.substring(start) : text.substring(start, end);
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}

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
package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class SliceLeafValueRootNode extends SliceNode implements MyColoredTreeCellRenderer {
  final List<SliceNode> myCachedChildren;

  SliceLeafValueRootNode(@NotNull Project project,
                         @NotNull SliceNode root,
                         @NotNull SliceUsage sliceUsage,
                         @NotNull List<SliceNode> children) {
    super(project, sliceUsage, root.targetEqualUsages);
    myCachedChildren = children;
  }

  @Override
  @NotNull
  public Collection<SliceNode> getChildren() {
    return myCachedChildren;
  }

  @Override
  public List<SliceNode> getCachedChildren() {
    return myCachedChildren;
  }

  @Override
  protected void update(PresentationData presentation) {
  }

  @Override
  public String toString() {
    return getNodeText();
  }

  public String getNodeText() {
    SliceUsage value = getValue();
    String text;
    if (value != null) {
      PsiElement element = value.getUsageInfo().getElement();
      text = element == null ? "" : element.getText();
    }
    else {
      text = "Other";
    }
    return "Value: " + text;
  }

  @Override
  public void customizeCellRenderer(@NotNull SliceUsageCellRendererBase renderer,
                                    @NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    SliceUsage usage = getValue();
    renderer.append("Value: ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (usage != null) {
      PsiElement element = usage.getElement();
      if (element == null) {
        renderer.append(UsageViewBundle.message("node.invalid") + " ", SliceUsageCellRendererBase.ourInvalidAttributes);
      }
      else {
        appendElementText(usage, element, renderer);
      }
    }
    else {
      renderer.append("Other", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  private static void appendElementText(@NotNull UsageInfo2UsageAdapter usage,
                                        @NotNull final PsiElement element,
                                        @NotNull final SliceUsageCellRendererBase renderer) {
    PsiFile file = element.getContainingFile();
    List<TextChunk> result = new ArrayList<>();
    ChunkExtractor.getExtractor(element.getContainingFile())
      .createTextChunks(usage, file.getText(), element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset(),
                        false, result);

    for (TextChunk chunk : result) {
      renderer.append(chunk.getText(), chunk.getSimpleAttributesIgnoreBackground());
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SliceLeafValueRootNode extends SliceNode implements MyColoredTreeCellRenderer {
  public final List<SliceNode> myCachedChildren;

  public SliceLeafValueRootNode(@NotNull Project project,
                                @NotNull SliceNode root,
                                @NotNull SliceUsage sliceUsage,
                                @NotNull List<SliceNode> children) {
    super(project, sliceUsage, root.targetEqualUsages);
    myCachedChildren = children;
  }

  @Override
  public @NotNull Collection<SliceNode> getChildren() {
    return myCachedChildren;
  }

  @Override
  public List<SliceNode> getCachedChildren() {
    return myCachedChildren;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    SliceUsage sliceUsage = getValue();
    if (sliceUsage != null) {
      sliceUsage.updateCachedPresentation();
    }
  }

  @Override
  public String toString() {
    return getNodeText();
  }

  @Override
  public String getNodeText() {
    SliceUsage value = getValue();
    String text;
    if (value != null) {
      PsiElement element = value.getUsageInfo().getElement();
      text = element == null ? "" : element.getText();
    }
    else {
      text = LangBundle.message("node.slice.other");
    }
    return LangBundle.message("node.slice.value.2", text);
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
    renderer.append(LangBundle.message("node.slice.value"), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (usage != null) {
      PsiElement element = usage.getElement();
      if (element == null) {
        renderer.append(UsageViewBundle.message("node.invalid") + " ", UsageTreeColors.INVALID_ATTRIBUTES);
      }
      else {
        appendElementText(usage, element, renderer);
      }
    }
    else {
      renderer.append(LangBundle.message("node.slice.other"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  private static void appendElementText(@NotNull UsageInfo2UsageAdapter usage,
                                        final @NotNull PsiElement element,
                                        final @NotNull SliceUsageCellRendererBase renderer) {
    PsiFile file = element.getContainingFile();
    List<TextChunk> result = new ArrayList<>();
    ChunkExtractor.getExtractor(element.getContainingFile())
      .appendTextChunks(usage, file.getText(), element.getTextOffset(), element.getTextRange().getEndOffset(),
                        false, result);

    for (TextChunk chunk : result) {
      renderer.append(chunk.getText(), chunk.getSimpleAttributesIgnoreBackground());
    }
  }
}

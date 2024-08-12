// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.ide.ExporterToTextFile;
import com.intellij.usages.UsageViewSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SliceToTextFileExporter implements ExporterToTextFile {
  private final SliceTreeBuilder myBuilder;
  private final @NotNull UsageViewSettings myUsageViewSettings;
  private static final String myLineSeparator = System.lineSeparator();

  public SliceToTextFileExporter(@NotNull SliceTreeBuilder builder, @NotNull UsageViewSettings usageViewSettings) {
    myBuilder = builder;
    myUsageViewSettings = usageViewSettings;
  }

  @Override
  public @NotNull String getReportText() {
    StringBuilder buffer = new StringBuilder();
    appendChildren(buffer, myBuilder.getRootSliceNode(), "");
    return buffer.toString();
  }

  private void appendNode(StringBuilder buffer, SliceNode node, String indent) {
    buffer.append(indent).append(node.getNodeText()).append(myLineSeparator);

    appendChildren(buffer, node, indent + "    ");
  }

  private void appendChildren(StringBuilder buffer, SliceNode node, String indent) {
    List<SliceNode> cachedChildren = node.getCachedChildren();
    if (cachedChildren != null) {
      for (SliceNode child : cachedChildren) {
        appendNode(buffer, child, indent);
      }
    }
    else {
      buffer.append(indent).append("...").append(myLineSeparator);
    }
  }

  @Override
  public @NotNull String getDefaultFilePath() {
    return myUsageViewSettings.getExportFileName();
  }

  @Override
  public void exportedTo(@NotNull String filePath) {
    myUsageViewSettings.setExportFileName(filePath);
  }

  @Override
  public boolean canExport() {
    return !myBuilder.analysisInProgress;
  }
}

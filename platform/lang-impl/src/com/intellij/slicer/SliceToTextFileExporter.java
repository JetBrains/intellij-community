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

import com.intellij.ide.ExporterToTextFile;
import com.intellij.usages.UsageViewSettings;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.TooManyListenersException;

/**
 * @author nik
 */
public class SliceToTextFileExporter implements ExporterToTextFile {
  private final SliceTreeBuilder myBuilder;
  private String myLineSeparator = SystemProperties.getLineSeparator();

  public SliceToTextFileExporter(SliceTreeBuilder builder) {
    myBuilder = builder;
  }

  @Override
  public JComponent getSettingsEditor() {
    return null;
  }

  @Override
  public void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException {
  }

  @Override
  public void removeSettingsChangedListener(ChangeListener listener) {
  }

  @NotNull
  @Override
  public String getReportText() {
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

  @NotNull
  @Override
  public String getDefaultFilePath() {
    return UsageViewSettings.getInstance().EXPORT_FILE_NAME;
  }

  @Override
  public void exportedTo(String filePath) {
    UsageViewSettings.getInstance().EXPORT_FILE_NAME = filePath;
  }

  @Override
  public boolean canExport() {
    return !myBuilder.analysisInProgress;
  }
}

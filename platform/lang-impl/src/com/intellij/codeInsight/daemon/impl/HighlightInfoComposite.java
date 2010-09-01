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

/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HighlightInfoComposite extends HighlightInfo {
  @NonNls private static final String HTML_HEADER = "<html>";
  @NonNls private static final String BODY_HEADER = "<body>";
  @NonNls private static final String HTML_FOOTER = "</html>";
  @NonNls private static final String BODY_FOOTER = "</body>";
  @NonNls private static final String LINE_BREAK = "\n<hr size=1 noshade>";

  public HighlightInfoComposite(@NotNull List<HighlightInfo> infos) {
    super(infos.get(0).type, infos.get(0).startOffset, infos.get(0).endOffset, createCompositeDescription(infos),
          createCompositeTooltip(infos));
    text = infos.get(0).text;
    highlighter = infos.get(0).highlighter;
    group = infos.get(0).group;
    quickFixActionMarkers = ContainerUtil.createEmptyCOWList();
    quickFixActionRanges = ContainerUtil.createEmptyCOWList();
    for (HighlightInfo info : infos) {
      if (info.quickFixActionMarkers != null) {
        quickFixActionMarkers.addAll(info.quickFixActionMarkers);
      }
      if (info.quickFixActionRanges != null) {
        quickFixActionRanges.addAll(info.quickFixActionRanges);
      }
    }
  }

  @Nullable
  private static String createCompositeDescription(List<HighlightInfo> infos) {
    StringBuilder description = new StringBuilder();
    boolean isNull = true;
    for (HighlightInfo info : infos) {
      String itemDescription = info.description;
      if (itemDescription != null) {
        itemDescription = itemDescription.trim();
        description.append(itemDescription);
        if (!itemDescription.endsWith(".")) {
          description.append('.');
        }
        description.append(' ');

        isNull = false;
      }
    }
    return isNull ? null : description.toString();
  }

  @Nullable
  private static String createCompositeTooltip(List<HighlightInfo> infos) {
    StringBuilder result = new StringBuilder();
    for (HighlightInfo info : infos) {
      String toolTip = info.toolTip;
      if (toolTip != null) {
        if (result.length() != 0) {
          result.append(LINE_BREAK);
        }
        toolTip = StringUtil.trimStart(toolTip, HTML_HEADER);
        toolTip = StringUtil.trimStart(toolTip, BODY_HEADER);
        toolTip = StringUtil.trimEnd(toolTip, HTML_FOOTER);
        toolTip = StringUtil.trimEnd(toolTip, BODY_FOOTER);
        result.append(toolTip);
      }
    }
    if (result.length() == 0) {
      return null;
    }
    result.insert(0, HTML_HEADER);
    result.append(HTML_FOOTER);
    return result.toString();
  }
}

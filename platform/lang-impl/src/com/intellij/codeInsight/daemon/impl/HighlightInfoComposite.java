/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class HighlightInfoComposite extends HighlightInfo {
  @NonNls private static final String LINE_BREAK = "<hr size=1 noshade>";

  HighlightInfoComposite(@NotNull List<HighlightInfo> infos) {
    super(null, null, infos.get(0).type, infos.get(0).startOffset, infos.get(0).endOffset, createCompositeDescription(infos),
          createCompositeTooltip(infos), infos.get(0).type.getSeverity(null), false, null, false, 0, infos.get(0).getProblemGroup(), infos.get(0).getGutterIconRenderer());
    highlighter = infos.get(0).getHighlighter();
    setGroup(infos.get(0).getGroup());
    List<Pair<IntentionActionDescriptor, RangeMarker>> markers = ContainerUtil.emptyList();
    List<Pair<IntentionActionDescriptor, TextRange>> ranges = ContainerUtil.emptyList();
    for (HighlightInfo info : infos) {
      if (info.quickFixActionMarkers != null) {
        if (markers == ContainerUtil.<Pair<IntentionActionDescriptor, RangeMarker>>emptyList()) markers = new ArrayList<>();
        markers.addAll(info.quickFixActionMarkers);
      }
      if (info.quickFixActionRanges != null) {
        if (ranges == ContainerUtil.<Pair<IntentionActionDescriptor, TextRange>>emptyList()) ranges = new ArrayList<>();
        ranges.addAll(info.quickFixActionRanges);
      }
    }
    quickFixActionMarkers = ContainerUtil.createLockFreeCopyOnWriteList(markers);
    quickFixActionRanges = ContainerUtil.createLockFreeCopyOnWriteList(ranges);
  }

  @Nullable
  private static String createCompositeDescription(List<HighlightInfo> infos) {
    StringBuilder description = new StringBuilder();
    boolean isNull = true;
    for (HighlightInfo info : infos) {
      String itemDescription = info.getDescription();
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
  private static String createCompositeTooltip(@NotNull List<HighlightInfo> infos) {
    StringBuilder result = new StringBuilder();
    for (HighlightInfo info : infos) {
      String toolTip = info.getToolTip();
      if (toolTip != null) {
        if (result.length() != 0) {
          result.append(LINE_BREAK);
        }
        toolTip = XmlStringUtil.stripHtml(toolTip);
        result.append(toolTip);
      }
    }
    if (result.length() == 0) {
      return null;
    }
    return XmlStringUtil.wrapInHtml(result);
  }
}

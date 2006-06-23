/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HighlightInfoComposite extends HighlightInfo {
  private static final @NonNls String HTML_HEADER = "<html><body>";
  private static final @NonNls String HTML_FOOTER = "</body></html>";
  private static final @NonNls String LINE_BREAK = "\n<hr size=1 noshade>";

  public HighlightInfoComposite(List<HighlightInfo> infos) {
    super(getType(infos), infos.get(0).startOffset, infos.get(0).endOffset, createCompositeDescription(infos),
          createCompositeTooltip(infos));
    text = infos.get(0).text;
    highlighter = infos.get(0).highlighter;
    group = infos.get(0).group;
    quickFixActionMarkers = new ArrayList<Pair<IntentionActionDescriptor, RangeMarker>>();
    quickFixActionRanges = new ArrayList<Pair<IntentionActionDescriptor, TextRange>>();
    for (HighlightInfo info : infos) {
      if (info.quickFixActionMarkers != null) {
        quickFixActionMarkers.addAll(info.quickFixActionMarkers);
      }
      if (info.quickFixActionRanges != null) {
        quickFixActionRanges.addAll(info.quickFixActionRanges);
      }
    }
  }

  private static HighlightInfoType getType(List<HighlightInfo> infos) {
    return infos.get(0).type;
  }

  @Nullable
  private static String createCompositeDescription(List<HighlightInfo> infos) {
    StringBuilder description = StringBuilderSpinAllocator.alloc();
    try {
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
    finally {
      StringBuilderSpinAllocator.dispose(description);
    }
  }

  @Nullable
  private static String createCompositeTooltip(List<HighlightInfo> infos) {
    StringBuilder result = StringBuilderSpinAllocator.alloc();
    try {
      for (HighlightInfo info : infos) {
        String toolTip = info.toolTip;
        if (toolTip != null) {
          if (result.length() != 0) {
            result.append(LINE_BREAK);
          }
          toolTip = StringUtil.trimStart(toolTip, HTML_HEADER);
          toolTip = StringUtil.trimEnd(toolTip, HTML_FOOTER);
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
    finally {
      StringBuilderSpinAllocator.dispose(result);
    }
  }

  public void addToolTipLine(String line) {
    toolTip = StringUtil.trimEnd(toolTip, HTML_FOOTER) + LINE_BREAK + line + HTML_FOOTER;
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class IndentUsageStatisticsImpl implements IndentUsageStatistics {
  private final List<LineIndentInfo> myLineInfos;

  private int myPreviousLineIndent;
  private int myPreviousRelativeIndent;

  private int myTotalLinesWithTabs = 0;
  private int myTotalLinesWithWhiteSpaces = 0;

  @SuppressWarnings("SSBasedInspection")
  private final Int2IntOpenHashMap myIndentToUsagesMap = new Int2IntOpenHashMap();
  private final List<IndentUsageInfo> myIndentUsages;
  private final Stack<IndentData> myParentIndents = new Stack<>(new IndentData(0, 0));

  public IndentUsageStatisticsImpl(@NotNull List<LineIndentInfo> lineInfos) {
    myLineInfos = lineInfos;
    buildIndentToUsagesMap();
    myIndentUsages = toIndentUsageList(myIndentToUsagesMap);
    myIndentUsages.sort((o1, o2) -> {
      int diff = o2.getTimesUsed() - o1.getTimesUsed();
      // indent 8 - 1 usage, indent 0 - 1 usage
      return diff == 0 ? o2.getIndentSize() - o1.getIndentSize() : diff;
    });
  }

  private static @NotNull List<IndentUsageInfo> toIndentUsageList(@NotNull Int2IntMap indentToUsages) {
    List<IndentUsageInfo> indentUsageInfos = new ArrayList<>(indentToUsages.size());
    for (Int2IntMap.Entry entry : indentToUsages.int2IntEntrySet()) {
      indentUsageInfos.add(new IndentUsageInfo(entry.getIntKey(), entry.getIntValue()));
    }
    return indentUsageInfos;
  }

  public void buildIndentToUsagesMap() {
    myPreviousLineIndent = 0;
    myPreviousRelativeIndent = 0;

    for (LineIndentInfo lineInfo : myLineInfos) {
      if (lineInfo.isLineWithTabs()) {
        myTotalLinesWithTabs++;
      }
      else if (lineInfo.isLineWithNormalIndent()) {
        handleNormalIndent(lineInfo.getIndentSize());
      }
    }
  }

  private @NotNull IndentData findParentIndent(int indent) {
    while (myParentIndents.size() != 1 && myParentIndents.peek().indent > indent) {
      myParentIndents.pop();
    }
    return myParentIndents.peek();
  }

  private void handleNormalIndent(int currentIndent) {
    int relativeIndent = currentIndent - myPreviousLineIndent;
    if (relativeIndent < 0) {
      IndentData indentData = findParentIndent(currentIndent);
      myPreviousLineIndent = indentData.indent;
      myPreviousRelativeIndent = indentData.relativeIndent;
      relativeIndent = currentIndent - myPreviousLineIndent;
    }

    if (relativeIndent == 0) {
      relativeIndent = myPreviousRelativeIndent;
    }
    else {
      myParentIndents.push(new IndentData(currentIndent, relativeIndent));
    }

    myIndentToUsagesMap.addTo(relativeIndent, 1);

    myPreviousRelativeIndent = relativeIndent;
    myPreviousLineIndent = currentIndent;

    if (currentIndent > 0) {
      myTotalLinesWithWhiteSpaces++;
    }
  }

  @Override
  public int getTotalLinesWithLeadingTabs() {
    return myTotalLinesWithTabs;
  }

  @Override
  public int getTotalLinesWithLeadingSpaces() {
    return myTotalLinesWithWhiteSpaces;
  }

  @Override
  public IndentUsageInfo getKMostUsedIndentInfo(int k) {
    return myIndentUsages.get(k);
  }

  @Override
  public int getTimesIndentUsed(int indent) {
    return myIndentToUsagesMap.get(indent);
  }

  @Override
  public int getTotalIndentSizesDetected() {
    return myIndentToUsagesMap.size();
  }

  private static final class IndentData {
    public final int indent;
    public final int relativeIndent;

    IndentData(int indent, int relativeIndent) {
      this.indent = indent;
      this.relativeIndent = relativeIndent;
    }
  }
}

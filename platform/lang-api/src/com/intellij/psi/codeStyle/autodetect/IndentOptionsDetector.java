/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.*;

public class IndentOptionsDetector {
  private static Logger LOG = Logger.getInstance("#com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptionsDetector");

  private static final double RATE_THRESHOLD = 0.8;
  private static final int MIN_LINES_THRESHOLD = 50;
  private static final int MAX_INDENT_TO_DETECT = 8;

  private final PsiFile myFile;
  private final Project myProject;
  private final Document myDocument;

  public IndentOptionsDetector(@NotNull PsiFile file) {
    myFile = file;
    myProject = file.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
  }

  @NotNull
  public IndentOptions getIndentOptions() {
    IndentOptions indentOptions = (IndentOptions)CodeStyleSettingsManager.getSettings(myProject).getIndentOptions(myFile.getFileType()).clone();

    if (myDocument != null) {
      List<LineIndentInfo> linesInfo = new LineIndentInfoBuilder(myDocument.getCharsSequence()).build();
      IndentUsageStatistics stats = new IndentUsageStatisticsImpl(linesInfo);
      adjustIndentOptions(indentOptions, stats);
    }

    return indentOptions;
  }

  private void adjustIndentOptions(@NotNull IndentOptions indentOptions, @NotNull IndentUsageStatistics stats) {
    int linesWithTabs = stats.getTotalLinesWithLeadingTabs();
    int linesWithWhiteSpaceIndent = stats.getTotalLinesWithLeadingSpaces();

    int totalLines = linesWithTabs + linesWithWhiteSpaceIndent;
    double lineWithTabsRate = (double)linesWithTabs / totalLines;

    if (linesWithTabs > MIN_LINES_THRESHOLD && lineWithTabsRate > RATE_THRESHOLD) {
      if (!indentOptions.USE_TAB_CHARACTER) {
        indentOptions.USE_TAB_CHARACTER = true;
        LOG.info("Detected tab usage in" + myFile);
      }
    }
    else if (linesWithWhiteSpaceIndent > MIN_LINES_THRESHOLD && (1 - lineWithTabsRate) > RATE_THRESHOLD) {
      int newIndentSize = getPositiveIndentSize(stats);
      if (newIndentSize > 0 && indentOptions.INDENT_SIZE != newIndentSize) {
        indentOptions.INDENT_SIZE = newIndentSize;
        LOG.info("Detected indent size: " + newIndentSize + " for file " + myFile);
      }
    }
  }

  private static int getPositiveIndentSize(@NotNull IndentUsageStatistics stats) {
    int totalIndentSizesDetected = stats.getTotalIndentSizesDetected();
    if (totalIndentSizesDetected == 0) return -1;

    IndentUsageInfo maxUsedIndentInfo = stats.getKMostUsedIndentInfo(0);
    int maxUsedIndentSize = maxUsedIndentInfo.getIndentSize();

    if (maxUsedIndentSize == 0) {
      if (totalIndentSizesDetected < 1) return -1;

      maxUsedIndentInfo = stats.getKMostUsedIndentInfo(1);
      maxUsedIndentSize = maxUsedIndentInfo.getIndentSize();
    }

    if (maxUsedIndentSize <= MAX_INDENT_TO_DETECT) {
      int totalUsagesWithoutZeroIndent = stats.getTotalLinesWithLeadingSpaces() - stats.getTimesIndentUsed(0);
      double usageRate = (double)maxUsedIndentInfo.getTimesUsed() / totalUsagesWithoutZeroIndent;
      if (usageRate > RATE_THRESHOLD) {
        return maxUsedIndentSize;
      }
    }

    return -1;
  }
}

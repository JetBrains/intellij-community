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

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

public class IndentOptionsDetectorImpl implements IndentOptionsDetector {
  private static Logger LOG = Logger.getInstance("#com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptionsDetector");

  private static final double RATE_THRESHOLD = 0.8;
  private static final int MAX_INDENT_TO_DETECT = 8;

  private final PsiFile myFile;
  private final Project myProject;
  private final Document myDocument;
  private final Language myLanguage;
  private final boolean myUseFormatterBasedLineIndentBuilder;

  public IndentOptionsDetectorImpl(@NotNull PsiFile file) {
    myFile = file;
    myLanguage = file.getLanguage();
    myProject = file.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
    myUseFormatterBasedLineIndentBuilder = Registry.is("editor.detect.indent.by.formatter");
  }

  @Override
  @NotNull
  public IndentOptions getIndentOptions() {
    IndentOptions indentOptions = (IndentOptions)CodeStyleSettingsManager.getSettings(myProject).getIndentOptions(myFile.getFileType()).clone();

    long start = System.currentTimeMillis();
    List<LineIndentInfo> linesInfo = calcLineIndentInfo();
    long end = System.currentTimeMillis();
    LOG.info("Formatter-based: " + myUseFormatterBasedLineIndentBuilder + ". Line info building time: " + (end - start));

    if (linesInfo != null) {
      IndentUsageStatistics stats = new IndentUsageStatisticsImpl(linesInfo);
      adjustIndentOptions(indentOptions, stats);
    }

    return indentOptions;
  }

  private List<LineIndentInfo> calcLineIndentInfo() {
    if (myDocument == null) return null;
    if (myUseFormatterBasedLineIndentBuilder) {
      return new FormatterBasedLineIndentInfoBuilder(myFile).build();
    }
    return new LineIndentInfoBuilder(myDocument.getCharsSequence(), myLanguage).build();
  }

  private void adjustIndentOptions(@NotNull IndentOptions indentOptions, @NotNull IndentUsageStatistics stats) {
    int linesWithTabs = stats.getTotalLinesWithLeadingTabs();
    int linesWithWhiteSpaceIndent = stats.getTotalLinesWithLeadingSpaces();

    if (linesWithTabs > linesWithWhiteSpaceIndent) {
      setUseTabs(indentOptions, true);
    }
    else if (linesWithWhiteSpaceIndent > linesWithTabs) {
      setUseTabs(indentOptions, false);

      int newIndentSize = getPositiveIndentSize(stats);
      if (newIndentSize > 0) {
        if (indentOptions.INDENT_SIZE != newIndentSize) {
          indentOptions.INDENT_SIZE = newIndentSize;
          LOG.debug("Detected indent size: " + newIndentSize + " for file " + myFile);
        }
      }
    }
  }

  private void setUseTabs(@NotNull IndentOptions indentOptions, boolean useTabs) {
    if (indentOptions.USE_TAB_CHARACTER != useTabs) {
      indentOptions.USE_TAB_CHARACTER = useTabs;
      LOG.debug("Tab usage set to " + useTabs + " for file " + myFile);
    }
  }

  private static int getPositiveIndentSize(@NotNull IndentUsageStatistics stats) {
    int totalIndentSizesDetected = stats.getTotalIndentSizesDetected();
    if (totalIndentSizesDetected == 0) return -1;

    IndentUsageInfo maxUsedIndentInfo = stats.getKMostUsedIndentInfo(0);
    int maxUsedIndentSize = maxUsedIndentInfo.getIndentSize();

    if (maxUsedIndentSize == 0) {
      if (totalIndentSizesDetected < 2) return -1;

      maxUsedIndentInfo = stats.getKMostUsedIndentInfo(1);
      maxUsedIndentSize = maxUsedIndentInfo.getIndentSize();
    }

    if (maxUsedIndentSize <= MAX_INDENT_TO_DETECT) {
      double usageRate = (double)maxUsedIndentInfo.getTimesUsed() / stats.getTotalLinesWithLeadingSpaces();
      if (usageRate > RATE_THRESHOLD) {
        return maxUsedIndentSize;
      }
    }

    return -1;
  }
}

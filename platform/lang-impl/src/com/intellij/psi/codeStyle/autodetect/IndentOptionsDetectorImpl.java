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

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

public class IndentOptionsDetectorImpl implements IndentOptionsDetector {
  private static Logger LOG = Logger.getInstance("#com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptionsDetector");

  private static final double RATE_THRESHOLD = 0.8;
  private static final int MAX_INDENT_TO_DETECT = 8;

  private final PsiFile myFile;
  private final Project myProject;
  private final Document myDocument;

  public IndentOptionsDetectorImpl(@NotNull PsiFile file) {
    myFile = file;
    myProject = file.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
  }

  @Override
  @NotNull
  public IndentOptions getIndentOptions() {
    IndentOptions indentOptions = (IndentOptions)CodeStyleSettingsManager.getSettings(myProject).getIndentOptions(myFile.getFileType()).clone();

    List<LineIndentInfo> linesInfo = calcLineIndentInfo();
    if (linesInfo != null) {
      IndentUsageStatistics stats = new IndentUsageStatisticsImpl(linesInfo);
      adjustIndentOptions(indentOptions, stats);
    }

    return indentOptions;
  }

  @Nullable
  private List<LineIndentInfo> calcLineIndentInfo() {
    if (myDocument == null || myDocument.getLineCount() < 3 || isFileBigToDetect()) {
      return null;
    }
    
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
    FormattingModelBuilder modelBuilder = LanguageFormatting.INSTANCE.forContext(myFile);
    if (modelBuilder == null) return null;
    
    FormattingModel model = modelBuilder.createModel(myFile, settings);
    Block rootBlock = model.getRootBlock();
    return new FormatterBasedLineIndentInfoBuilder(myDocument, rootBlock).build();
  }

  private boolean isFileBigToDetect() {
    VirtualFile file = myFile.getVirtualFile();
    if (file != null && file.getLength() > FileUtilRt.MEGABYTE) {
      LOG.debug("Indent detector disabled for this file");
      return true;
    }
    return false;
  }

  private void adjustIndentOptions(@NotNull IndentOptions indentOptions, @NotNull IndentUsageStatistics stats) {
    if (isTabsUsed(stats)) {
      adjustForTabUsage(indentOptions);
    }
    else if (isSpacesUsed(stats)) {
      indentOptions.USE_TAB_CHARACTER = false;
      
      int newIndentSize = getPositiveIndentSize(stats);
      if (newIndentSize > 0) {
        if (indentOptions.INDENT_SIZE != newIndentSize) {
          indentOptions.INDENT_SIZE = newIndentSize;
          LOG.debug("Detected indent size: " + newIndentSize + " for file " + myFile);
        }
      }
    }
  }

  private static boolean isSpacesUsed(IndentUsageStatistics stats) {
    int spaces = stats.getTotalLinesWithLeadingSpaces();
    int total = stats.getTotalLinesWithLeadingSpaces() + stats.getTotalLinesWithLeadingTabs();
    return (double)spaces / total > RATE_THRESHOLD;
  }

  private static boolean isTabsUsed(IndentUsageStatistics stats) {
    return stats.getTotalLinesWithLeadingTabs() > stats.getTotalLinesWithLeadingSpaces();
  }

  private void adjustForTabUsage(@NotNull IndentOptions indentOptions) {
    if (indentOptions.USE_TAB_CHARACTER) return;
    
    int continuationRatio = indentOptions.INDENT_SIZE == 0 ? 1 : indentOptions.CONTINUATION_INDENT_SIZE / indentOptions.INDENT_SIZE;
    
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.INDENT_SIZE = indentOptions.TAB_SIZE;
    indentOptions.CONTINUATION_INDENT_SIZE = indentOptions.TAB_SIZE * continuationRatio;
    
    LOG.debug("Using tabs for: " + myFile);
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

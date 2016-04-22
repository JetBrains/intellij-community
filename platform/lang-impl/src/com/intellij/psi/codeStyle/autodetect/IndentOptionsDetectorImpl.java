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
    IndentOptions indentOptions =
      (IndentOptions)CodeStyleSettingsManager.getSettings(myProject).getIndentOptions(myFile.getFileType()).clone();

    List<LineIndentInfo> linesInfo = calcLineIndentInfo();
    if (linesInfo != null) {
      IndentUsageStatistics stats = new IndentUsageStatisticsImpl(linesInfo);
      IndentOptionsAdjuster adjuster = new IndentOptionsAdjuster(stats);
      adjuster.adjust(indentOptions);
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
      return true;
    }
    return false;
  }
}


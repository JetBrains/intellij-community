// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;

@ApiStatus.Internal
public final class IndentOptionsDetectorImpl implements IndentOptionsDetector {
  private final VirtualFile myFile;
  private final Project myProject;
  private final Document myDocument;
  private final ProgressIndicator myProgressIndicator;

  public IndentOptionsDetectorImpl(@NotNull Project project,
                                   @NotNull VirtualFile file,
                                   @NotNull Document document,
                                   @NotNull ProgressIndicator indicator) {
    myFile = file;
    myProject = project;
    myDocument = document;
    myProgressIndicator = indicator;
  }

  @Override
  public @Nullable IndentOptionsAdjuster getIndentOptionsAdjuster() {
    try {
      List<LineIndentInfo> linesInfo = calcLineIndentInfo(myProgressIndicator);
      if (linesInfo != null) {
        return new IndentOptionsAdjusterImpl(new IndentUsageStatisticsImpl(linesInfo));
      }
    }
    catch (IndexNotReadyException ignore) { }
    return null;
  }

  @Override
  public @NotNull IndentOptions getIndentOptions() {
    IndentOptions indentOptions =
      (IndentOptions)CodeStyle.getSettings(myProject, myFile).getIndentOptions(myFile.getFileType()).clone();

    IndentOptionsAdjuster adjuster = getIndentOptionsAdjuster();
    if (adjuster != null) {
      adjuster.adjust(indentOptions);
    }

    return indentOptions;
  }

  private @Nullable List<LineIndentInfo> calcLineIndentInfo(@Nullable ProgressIndicator indicator) {
    if (myDocument.getLineCount() < 3 || isFileBigToDetect()) {
      return null;
    }
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (psiFile == null || !isUpToDate(psiFile, myDocument)) {
      return null;
    }

    CodeStyleSettings settings = CodeStyle.getSettings(myProject, myFile);
    FormattingModelBuilder modelBuilder = LanguageFormatting.INSTANCE.forContext(psiFile);
    if (modelBuilder == null) return null;

    FormattingModel model = modelBuilder.createModel(FormattingContext.create(psiFile, settings));
    Block rootBlock = model.getRootBlock();
    return new FormatterBasedLineIndentInfoBuilder(myDocument, rootBlock, indicator).build();
  }

  private boolean isUpToDate(@NotNull PsiFile file, @NotNull Document document) {
    return PsiDocumentManager.getInstance(myProject).isCommitted(myDocument) &&
           file.isValid() && file.getTextLength() == document.getTextLength();
  }

  private boolean isFileBigToDetect() {
    if (myFile.getLength() > FileUtilRt.MEGABYTE) {
      return true;
    }
    return false;
  }
}


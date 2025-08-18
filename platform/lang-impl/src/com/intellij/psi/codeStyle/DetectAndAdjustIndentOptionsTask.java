// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.autodetect.IndentOptionsAdjuster;
import com.intellij.psi.codeStyle.autodetect.IndentOptionsDetectorImpl;
import com.intellij.util.Time;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ExecutorService;


final class DetectAndAdjustIndentOptionsTask {
  private static final ExecutorService BOUNDED_EXECUTOR = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
    "DetectableIndentOptionsProvider Pool");
  private static final Logger LOG = Logger.getInstance(DetectAndAdjustIndentOptionsTask.class);
  private static final int INDENT_COMPUTATION_TIMEOUT = 5 * Time.SECOND;

  private final Document myDocument;
  private final Project myProject;
  private final TimeStampedIndentOptions myOptionsToAdjust;
  private final CodeStyleSettings mySettings;

  DetectAndAdjustIndentOptionsTask(@NotNull Project project, @NotNull Document document, @NotNull TimeStampedIndentOptions toAdjust,
                                   @NotNull CodeStyleSettings settings) {
    myProject = project;
    myDocument = document;
    myOptionsToAdjust = toAdjust;
    mySettings = settings;
  }
  
  private VirtualFile getFile() {
    return FileDocumentManager.getInstance().getFile(myDocument);
  }

  private @NotNull Runnable calcIndentAdjuster(@NotNull ProgressIndicator indicator) {
    VirtualFile file = getFile();
    IndentOptionsAdjuster adjuster = file == null ? null :
                                     new IndentOptionsDetectorImpl(myProject, file, myDocument, indicator).getIndentOptionsAdjuster();
    return adjuster != null ? () -> adjustOptions(adjuster) : EmptyRunnable.INSTANCE;
  }

  private void adjustOptions(IndentOptionsAdjuster adjuster) {
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return;

    final IndentOptions currentDefault = getDefaultIndentOptions(myProject, virtualFile, myDocument, mySettings);
    myOptionsToAdjust.copyFrom(currentDefault);

    adjuster.adjust(myOptionsToAdjust);
    myOptionsToAdjust.setTimeStamp(myDocument.getModificationStamp());
    myOptionsToAdjust.setOriginalIndentOptionsHash(currentDefault.hashCode());

    if (!currentDefault.equals(myOptionsToAdjust)) {
      myOptionsToAdjust.setDetected(true);
      myOptionsToAdjust.setOverrideLanguageOptions(true);
      CodeStyleSettingsManager.getInstance(myProject).fireCodeStyleSettingsChanged(virtualFile);
    }
  }

  private void logTooLongComputation() {
    VirtualFile file = getFile();
    String fileName = file != null ? file.getName() : "";
    LOG.debug("Indent detection is too long for: " + fileName);
  }

  void scheduleInBackgroundForCommittedDocument() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      calcIndentAdjuster(new DumbProgressIndicator()).run();
    }
    else {
      ReadAction
        .nonBlocking(() -> {
          Runnable indentAdjuster = ProgressIndicatorUtils.withTimeout(INDENT_COMPUTATION_TIMEOUT, () ->
            calcIndentAdjuster(Objects.requireNonNull(ProgressIndicatorProvider.getGlobalProgressIndicator())));
          if (indentAdjuster == null) {
            logTooLongComputation();
            return EmptyRunnable.INSTANCE;
          }
          return indentAdjuster;
        })
        .finishOnUiThread(ModalityState.defaultModalityState(), Runnable::run)
        .withDocumentsCommitted(myProject)
        .submit(BOUNDED_EXECUTOR);
    }
  }

  static @NotNull TimeStampedIndentOptions getDefaultIndentOptions(@NotNull Project project, @NotNull VirtualFile file, @NotNull Document document,
                                                                   @NotNull CodeStyleSettings settings) {
    FileType fileType = file.getFileType();
    return new TimeStampedIndentOptions(settings.getIndentOptions(fileType), document.getModificationStamp());
  }

  
}
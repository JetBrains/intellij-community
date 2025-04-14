// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.FileViewProviderUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.util.HighlightVisitorScopeKt.HighlightVisitorScope;
import static com.intellij.codeInspection.options.OptPane.checkbox;

public final class HighlightVisitorBasedInspection extends GlobalSimpleInspectionTool {
  public static final String SHORT_NAME = HighlightInfo.ANNOTATOR_INSPECTION_SHORT_NAME;
  @SuppressWarnings("WeakerAccess") // made public for serialization
  public boolean highlightErrorElements = true;
  @SuppressWarnings("WeakerAccess") // made public for serialization
  public boolean runAnnotators = true;
  @SuppressWarnings("WeakerAccess") // made public for serialization
  public boolean runVisitors = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      checkbox("highlightErrorElements", InspectionsBundle.message("inspection.annotator.option.highlight.syntax")),
      checkbox("runAnnotators", InspectionsBundle.message("inspection.annotator.option.run.annotators")),
      checkbox("runVisitors", InspectionsBundle.message("inspection.annotator.option.run.highlight.visitors"))
    );
  }

  public @NotNull HighlightVisitorBasedInspection setHighlightErrorElements(boolean value) {
    highlightErrorElements = value;
    return this;
  }
  public @NotNull HighlightVisitorBasedInspection setRunAnnotators(boolean value) {
    runAnnotators = value;
    return this;
  }
  public @NotNull HighlightVisitorBasedInspection setRunVisitors(boolean value) {
    runVisitors = value;
    return this;
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void checkFile(@NotNull PsiFile psiFile,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    for (HighlightInfo info : runAnnotatorsInGeneralHighlighting(psiFile, highlightErrorElements, runAnnotators, runVisitors)) {
      TextRange range = new TextRange(info.startOffset, info.endOffset);
      PsiElement element = psiFile.findElementAt(info.startOffset);

      while (element != null && !element.getTextRange().contains(range)) {
        element = element.getParent();
      }

      if (element == null) {
        element = psiFile;
      }

      GlobalInspectionUtil.createProblem(element, info, range.shiftRight(-element.getNode().getStartOffset()),
        info.getProblemGroup(), manager, problemDescriptionsProcessor, globalContext);
    }
  }

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return getGeneralGroupName();
  }

  public static @NotNull List<HighlightInfo> runAnnotatorsInGeneralHighlighting(@NotNull PsiFile psiFile,
                                                                                boolean highlightErrorElements,
                                                                                boolean runAnnotators,
                                                                                boolean runVisitors) {
    int startOffset = 0;
    int endOffset = psiFile.getTextLength();
    ProperTextRange visibleRange = ProperTextRange.create(psiFile.getTextRange());
    return runAnnotatorsInGeneralHighlighting(psiFile, startOffset, endOffset, visibleRange, highlightErrorElements, runAnnotators, runVisitors);
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public static @NotNull List<HighlightInfo> runAnnotatorsInGeneralHighlighting(@NotNull PsiFile psiFile,
                                                                              int startOffset,
                                                                              int endOffset,
                                                                              @NotNull ProperTextRange visibleRange,
                                                                              boolean highlightErrorElements,
                                                                              boolean runAnnotators,
                                                                              boolean runVisitors) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = psiFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return Collections.emptyList();
    DaemonProgressIndicator daemonProgressIndicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    // in case the inspection is running in batch mode
    // todo IJPL-339 figure out what is the correct context here
    CodeInsightContext context = FileViewProviderUtil.getCodeInsightContext(psiFile);
    HighlightingSessionImpl.getOrCreateHighlightingSession(psiFile, context, daemonProgressIndicator, visibleRange,
                                                           TextRange.EMPTY_RANGE);
    GeneralHighlightingPass ghp =
    new GeneralHighlightingPass(psiFile, document, startOffset, endOffset, true, visibleRange, null,
                                runAnnotators, runVisitors, highlightErrorElements, HighlightInfoUpdater.EMPTY);
    InjectedGeneralHighlightingPass ighp = new InjectedGeneralHighlightingPass(psiFile, document, null, startOffset, endOffset, true,
                                                                               visibleRange, null,
                                                                               runAnnotators, runVisitors, highlightErrorElements, HighlightInfoUpdater.EMPTY);
    ighp.setContext(context);
    String fileName = psiFile.getName();
    List<HighlightInfo> result = new ArrayList<>();
    IJTracer tracer = TelemetryManager.Companion.getTracer(HighlightVisitorScope);

    for (TextEditorHighlightingPass pass : List.of(ghp, ighp)) {
      TraceKt.use(tracer.spanBuilder(pass.getClass().getSimpleName()).setAttribute("file", fileName), __ -> {
        pass.doCollectInformation(daemonProgressIndicator);
        List<HighlightInfo> infos = pass.getInfos();
        for (HighlightInfo info : infos) {
          if (info == null) continue;
          if (info.getSeverity().compareTo(HighlightSeverity.INFORMATION) > 0) {
            result.add(info);
          }
        }
        return null;
      });
    }

    return result;
  }
}

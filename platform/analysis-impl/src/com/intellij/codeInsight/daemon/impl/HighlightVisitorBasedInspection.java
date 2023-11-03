// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.util.HighlightVisitorScopeKt.HighlightVisitorScope;
import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.runWithSpan;

public final class HighlightVisitorBasedInspection extends GlobalSimpleInspectionTool {
  public static final String SHORT_NAME = "Annotator";
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

  public static @NotNull List<HighlightInfo> runAnnotatorsInGeneralHighlighting(@NotNull PsiFile file,
                                                                                boolean highlightErrorElements,
                                                                                boolean runAnnotators,
                                                                                boolean runVisitors) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return Collections.emptyList();
    DaemonProgressIndicator daemonProgressIndicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    HighlightingSessionImpl.getOrCreateHighlightingSession(file, daemonProgressIndicator, ProperTextRange.create(file.getTextRange()));
    TextEditorHighlightingPassRegistrarEx passRegistrarEx = TextEditorHighlightingPassRegistrarEx.getInstanceEx(project);
    List<TextEditorHighlightingPass> passes = passRegistrarEx.instantiateMainPasses(file, document, HighlightInfoProcessor.getEmpty());
    List<GeneralHighlightingPass> gpasses = ContainerUtil.filterIsInstance(passes, GeneralHighlightingPass.class);
    if (!runVisitors) {
      for (GeneralHighlightingPass gpass : gpasses) {
        gpass.setHighlightVisitorProducer(() -> {
          gpass.incVisitorUsageCount(1);
          return List.of(new DefaultHighlightVisitor(project, highlightErrorElements, runAnnotators, true));
        });
      }
    }

    String fileName = file.getName();
    List<HighlightInfo> result = new ArrayList<>();
    IJTracer tracer = TelemetryManager.Companion.getTracer(HighlightVisitorScope);

    for (TextEditorHighlightingPass pass : gpasses) {
      runWithSpan(tracer, pass.getClass().getSimpleName(), span -> {
        span.setAttribute("file", fileName);

        pass.doCollectInformation(daemonProgressIndicator);
        List<HighlightInfo> infos = pass.getInfos();
        for (HighlightInfo info : infos) {
          if (info != null && info.getSeverity().compareTo(HighlightSeverity.INFORMATION) > 0) {
            result.add(info);
          }
        }
      });
    }

    return result;
  }
}

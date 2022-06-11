// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DefaultHighlightVisitorBasedInspection extends GlobalSimpleInspectionTool {
  private final boolean highlightErrorElements;
  private final boolean runAnnotators;

  protected DefaultHighlightVisitorBasedInspection(boolean highlightErrorElements, boolean runAnnotators) {
    this.highlightErrorElements = highlightErrorElements;
    this.runAnnotators = runAnnotators;
  }

  public static class AnnotatorBasedInspection extends DefaultHighlightVisitorBasedInspection {
    private static final @NonNls String ANNOTATOR_SHORT_NAME = "Annotator";

    public AnnotatorBasedInspection() {
      super(false, true);
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
      return AnalysisBundle.message("inspection.display.name.annotator");
    }

    @Override
    public @NotNull String getShortName() {
      return ANNOTATOR_SHORT_NAME;
    }

  }
  public static class SyntaxErrorInspection extends DefaultHighlightVisitorBasedInspection {
    public SyntaxErrorInspection() {
      super(true, false);
    }
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return AnalysisBundle.message("inspection.display.name.syntax.error");
    }

    @NotNull
    @Override
    public String getShortName() {
      return "SyntaxError";
    }
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void checkFile(@NotNull PsiFile originalFile,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    for (Pair<PsiFile, HighlightInfo> pair : runAnnotatorsInGeneralHighlighting(originalFile, highlightErrorElements, runAnnotators)) {
      PsiFile file = pair.first;
      HighlightInfo info = pair.second;
      TextRange range = new TextRange(info.startOffset, info.endOffset);
      PsiElement element = file.findElementAt(info.startOffset);

      while (element != null && !element.getTextRange().contains(range)) {
        element = element.getParent();
      }

      if (element == null) {
        element = file;
      }

      GlobalInspectionUtil.createProblem(element, info, range.shiftRight(-element.getNode().getStartOffset()),
        info.getProblemGroup(), manager, problemDescriptionsProcessor, globalContext);
    }
  }

  @NotNull
  public static List<Pair<PsiFile,HighlightInfo>> runAnnotatorsInGeneralHighlighting(@NotNull PsiFile file,
                                                                                     boolean highlightErrorElements,
                                                                                     boolean runAnnotators) {
    ProgressIndicator indicator = ProgressManager.getGlobalProgressIndicator();
    MyPsiElementVisitor visitor = new MyPsiElementVisitor(highlightErrorElements, runAnnotators);
    if (indicator instanceof DaemonProgressIndicator) {
      file.accept(visitor);
    }
    else {
      DaemonProgressIndicator progress = new DaemonProgressIndicator();
      HighlightingSessionImpl.createHighlightingSession(file, progress, null, ProperTextRange.create(file.getTextRange()), false);
      ProgressManager.getInstance().runProcess(() -> file.accept(visitor), progress);
    }
    return visitor.result;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return getGeneralGroupName();
  }

  private static class MyPsiElementVisitor extends PsiElementVisitor {
    private final boolean highlightErrorElements;
    private final boolean runAnnotators;
    private final List<Pair<PsiFile, HighlightInfo>> result = new ArrayList<>();

    MyPsiElementVisitor(boolean highlightErrorElements, boolean runAnnotators) {
      this.highlightErrorElements = highlightErrorElements;
      this.runAnnotators = runAnnotators;
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) {
        return;
      }

      result.addAll(runAnnotatorsInGeneralHighlightingPass(file, highlightErrorElements, runAnnotators));
    }
  }

  @NotNull
  private static List<Pair<PsiFile, HighlightInfo>> runAnnotatorsInGeneralHighlightingPass(@NotNull PsiFile file,
                                                                                           boolean highlightErrorElements,
                                                                                           boolean runAnnotators) {
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return Collections.emptyList();
    ProgressIndicator progress = ProgressManager.getGlobalProgressIndicator();
    DaemonProgressIndicator daemonProgressIndicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    HighlightingSessionImpl.getOrCreateHighlightingSession(file, daemonProgressIndicator, ProperTextRange.create(file.getTextRange()));
    TextEditorHighlightingPassRegistrarEx passRegistrarEx = TextEditorHighlightingPassRegistrarEx.getInstanceEx(project);
    List<TextEditorHighlightingPass> passes = passRegistrarEx.instantiateMainPasses(file, document, HighlightInfoProcessor.getEmpty());
    List<GeneralHighlightingPass> gpasses = ContainerUtil.filterIsInstance(passes, GeneralHighlightingPass.class);
    for (GeneralHighlightingPass gpass : gpasses) {
      gpass.setHighlightVisitorProducer(() -> {
        gpass.incVisitorUsageCount(1);

        HighlightVisitor visitor = new DefaultHighlightVisitor(project, highlightErrorElements, runAnnotators, true);
        return new HighlightVisitor[]{visitor};
      });
    }

    List<Pair<PsiFile, HighlightInfo>> result = new ArrayList<>();
    for (TextEditorHighlightingPass pass : gpasses) {
      pass.doCollectInformation(progress);
      List<HighlightInfo> infos = pass.getInfos();
      for (HighlightInfo info : infos) {
        if (info != null && info.getSeverity().compareTo(HighlightSeverity.INFORMATION) > 0) {
          result.add(Pair.create(file, info));
        }
      }
    }
    return result;
  }
}

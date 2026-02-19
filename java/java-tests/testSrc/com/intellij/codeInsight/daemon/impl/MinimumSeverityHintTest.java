// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class MinimumSeverityHintTest extends DaemonAnalyzerTestCase {
  private static volatile HighlightSeverity MY_CRAZY_SEVERITY;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
    UndoManager.getInstance(myProject);
    DaemonCodeAnalyzer.getInstance(getProject()).setUpdateByTimerEnabled(true);
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    DaemonProgressIndicator.runInDebugMode(() -> super.runTestRunnable(testRunnable));
  }

  public void testMinimumSeverityHintDoesPropagateToAnnotators() {
    MY_CRAZY_SEVERITY = new HighlightSeverity("myCrazy severity", 987);
    MainPassesRunner runner = configureTestFile();
    MyHintedAnnotator annotator = new MyHintedAnnotator();
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{annotator}, () -> {
      runner.runMainPasses(List.of(getFile().getVirtualFile()), MY_CRAZY_SEVERITY);
    });
    assertTrue(annotator.didIDoIt());
  }
  public void testMinimumSeverityHintStaysUndefinedInSaneAnnotators() {
    MY_CRAZY_SEVERITY = null;
    MainPassesRunner runner = configureTestFile();
    MyHintedAnnotator annotator = new MyHintedAnnotator();
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{annotator}, () -> {
      runner.runMainPasses(List.of(getFile().getVirtualFile()));
    });
    assertTrue(annotator.didIDoIt());
  }
  public void testMinimumSeverityHintDoesPropagateToInspections() {
    MY_CRAZY_SEVERITY = new HighlightSeverity("myCrazy severity", 987);
    MyHintedInspection inspection = new MyHintedInspection();
    enableInspectionTool(inspection);
    MainPassesRunner runner = configureTestFile();
    runner.runMainPasses(List.of(getFile().getVirtualFile()), MY_CRAZY_SEVERITY);
    assertTrue(inspection.started);
  }
  public void testMinimumSeverityStaysNullInNormalInspections() {
    MY_CRAZY_SEVERITY = null;
    MyHintedInspection inspection = new MyHintedInspection();
    enableInspectionTool(inspection);
    MainPassesRunner runner = configureTestFile();
    runner.runMainPasses(List.of(getFile().getVirtualFile()));
    assertTrue(inspection.started);
  }

  @NotNull
  private MainPassesRunner configureTestFile() {
    configureByText(JavaFileType.INSTANCE, "class XXX {}");
    return new MainPassesRunner(getProject(), "hehe", null);
  }

  public static class MyHintedAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      assertEquals(MY_CRAZY_SEVERITY, holder.getCurrentAnnotationSession().getMinimumSeverity());
      iDidIt();
    }
  }
  private static class MyHintedInspection extends DaemonInspectionsRespondToChangesTest.MyInspectionBase {
    volatile boolean started;
    @Override
    public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
      assertEquals(MY_CRAZY_SEVERITY, session.getMinimumSeverity());
      started = true;
    }
  }
}


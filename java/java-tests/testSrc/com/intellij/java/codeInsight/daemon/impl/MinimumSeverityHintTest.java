// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.MainPassesRunner;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class MinimumSeverityHintTest {
  private static volatile HighlightSeverity MY_CRAZY_SEVERITY;

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTool(new UnusedDeclarationInspection());
    UndoManager.getInstance(fixture.getProject());
    fixture.getDaemonCodeAnalyzer().setUpdateByTimerEnabled(true);
  }

  private static void runTest(ThrowableRunnable<Throwable> test) throws Throwable {
    DaemonProgressIndicator.runInDebugMode(test);
  }

  @Test
  public void testMinimumSeverityHintDoesPropagateToAnnotators() throws Throwable {
    runTest(() -> {
      MY_CRAZY_SEVERITY = new HighlightSeverity("myCrazy severity", 987);
      MainPassesRunner runner = configureTestFile();
      MyHintedAnnotator annotator = new MyHintedAnnotator();
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{annotator}, () -> {
        EdtTestUtil.runInEdtAndWait(() -> runner.runMainPasses(List.of(daemonFixture.get().getFile().getVirtualFile()), MY_CRAZY_SEVERITY));
      });
      assertTrue(annotator.didIDoIt());
    });
  }
  @Test
  public void testMinimumSeverityHintStaysUndefinedInSaneAnnotators() throws Throwable {
    runTest(() -> {
      MY_CRAZY_SEVERITY = null;
      MainPassesRunner runner = configureTestFile();
      MyHintedAnnotator annotator = new MyHintedAnnotator();
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(JavaFileType.INSTANCE.getLanguage(), new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{annotator}, () -> {
        EdtTestUtil.runInEdtAndWait(() -> runner.runMainPasses(List.of(daemonFixture.get().getFile().getVirtualFile())));
      });
      assertTrue(annotator.didIDoIt());
    });
  }
  @Test
  public void testMinimumSeverityHintDoesPropagateToInspections() throws Throwable {
    runTest(() -> {
      MY_CRAZY_SEVERITY = new HighlightSeverity("myCrazy severity", 987);
      MyHintedInspection inspection = new MyHintedInspection();
      daemonFixture.get().enableInspectionTool(inspection);
      MainPassesRunner runner = configureTestFile();
      EdtTestUtil.runInEdtAndWait(() -> runner.runMainPasses(List.of(daemonFixture.get().getFile().getVirtualFile()), MY_CRAZY_SEVERITY));
      assertTrue(inspection.started);
    });
  }
  @Test
  public void testMinimumSeverityStaysNullInNormalInspections() throws Throwable {
    runTest(() -> {
      MY_CRAZY_SEVERITY = null;
      MyHintedInspection inspection = new MyHintedInspection();
      daemonFixture.get().enableInspectionTool(inspection);
      MainPassesRunner runner = configureTestFile();
      EdtTestUtil.runInEdtAndWait(() -> runner.runMainPasses(List.of(daemonFixture.get().getFile().getVirtualFile())));
      assertTrue(inspection.started);
    });
  }

  @NotNull
  private MainPassesRunner configureTestFile() {
    @Language("JAVA")
    String text = "class XXX {}";
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText(JavaFileType.INSTANCE, text);
    return new MainPassesRunner(fixture.getProject(), "hehe", null);
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

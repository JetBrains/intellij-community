// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@SkipSlowTestLocally
@HardwareAgentRequired
public class BigJavaFilesPerformanceTest extends LightJavaCodeInsightFixtureTestCase5 {

  @Test
  public void testHighlightingJComponent() {
    long mean = doHighlightingTest("/codeInsight/bigFilesPerformance/JComponent.java", 9);
    Assertions.assertTrue(mean < 10_000);
  }

  @Test
  public void testHighlightingWithInspectionsJComponent() {
    MadTestingUtil.enableDefaultInspections(getFixture().getProject());
    long mean = doHighlightingTest("/codeInsight/bigFilesPerformance/JComponent.java", 9);
    Assertions.assertTrue(mean < 15_000);
  }

  @Test
  public void testTypingJComponent(){
    long mean = doTypingTest("/codeInsight/bigFilesPerformance/JComponent.java", 50, false);
    Assertions.assertTrue(mean < 25);
  }

  @Test
  public void testTypingWithInspectionsJComponent(){
    MadTestingUtil.enableDefaultInspections(getFixture().getProject());
    long mean = doTypingTest("/codeInsight/bigFilesPerformance/JComponent.java", 50, true);
    Assertions.assertTrue(mean < 50);
  }

  @Test
  public void testHighlightingWithInspectionsThinletBig() {
    MadTestingUtil.enableDefaultInspections(getFixture().getProject());
    long mean = doHighlightingTest("/psi/resolve/ThinletBig.java", 9);
    System.out.println("Warnings: " + getFixture().doHighlighting().size());
    Assertions.assertTrue(mean < 75_000);
  }

  @Test
  public void testTypingWithInspectionsThinletBig() {
    MadTestingUtil.enableDefaultInspections(getFixture().getProject());
    long mean = doTypingTest("/psi/resolve/ThinletBig.java", 50,true);
    Assertions.assertTrue(mean < 70, "Slow typing, delay is " + mean);
  }

  private void doTest(String filename, int samples, Consumer<DaemonListener> processSample) {
    Timings.getStatistics();
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByFile(filename);

    setupDaemonAnalyzer(fixture.getTestRootDisposable(), fixture.getEditor());
    DaemonListener daemonListener = new DaemonListener(fixture.getTestRootDisposable(), fixture.getProject());
    try {
      waitDaemonToFinish(daemonListener);
    } catch (Exception ignored) {
    }

    for (int caretOffset: findVariableOffsets(fixture.getFile(), samples)) {
      EdtTestUtil.runInEdtAndWait(() -> fixture.getEditor().getCaretModel().moveToOffset(caretOffset));
      processSample.accept(daemonListener);
    }
  }

  private long doHighlightingTest(String filename, int samples) {
    List<Long> highlightingTimings = new ArrayList<>(samples);
    doTest(filename, samples, (daemonListener) -> {
      getFixture().type("a");
      try {
        long highlightingTime = waitDaemonToFinish(daemonListener);
        highlightingTimings.add(highlightingTime);
      } catch (Exception ignored) {
      }
    });
    return calculateMean(highlightingTimings);
  }

  private long doTypingTest(String filename, int samples, boolean waitDaemonToStart) {
    List<Long> typingTimings = new ArrayList<>(samples);
    doTest(filename, samples, (daemonListener) -> {
      long typingTime = TimeoutUtil.measureExecutionTime(() -> {
        getFixture().type("a");
      });
      typingTimings.add(typingTime);
      getFixture().type("\b");
      if (waitDaemonToStart) {
        try {
          waitDaemonToStart(daemonListener);
        } catch (Exception ignored){
        }
      }
    });

    return calculateMean(typingTimings);
  }

  private static void setupDaemonAnalyzer(Disposable context, Editor editor) {
    Project project = Objects.requireNonNull(editor.getProject());
    EdtTestUtil.runInEdtAndWait(() -> {
      EditorTracker.getInstance(project).setActiveEditors(List.of(editor));
    });
    DaemonCodeAnalyzer.getInstance(project).setUpdateByTimerEnabled(true);
    int reparseDelay = DaemonCodeAnalyzerSettings.getInstance().getAutoReparseDelay();
    Disposer.register(context, () -> DaemonCodeAnalyzerSettings.getInstance().setAutoReparseDelay(reparseDelay));
    DaemonCodeAnalyzerSettings.getInstance().setAutoReparseDelay(0);
  }

  private static void waitDaemonToStart(DaemonListener listener) throws Exception {
    CompletableFuture<Boolean> isStarted = new CompletableFuture<>();
    listener.daemonStarted(() -> isStarted.complete(true));
    isStarted.get(10, TimeUnit.SECONDS);
  }

  private static long waitDaemonToFinish(DaemonListener listener) throws Exception {
    CompletableFuture<Boolean> isFinished = new CompletableFuture<>();
    AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    AtomicLong duration = new AtomicLong();
    listener.daemonStarted(() -> startTime.set(System.currentTimeMillis()));
    listener.daemonFinished(() -> {
      long start = startTime.get();
      duration.set(System.currentTimeMillis() - start);
      isFinished.complete(true);
    });
    isFinished.get(40, TimeUnit.SECONDS);
    return duration.get();
  }

  private static class DaemonListener {
    private Runnable daemonStarted = () -> {};
    private Runnable daemonFinished = () -> {};

    void daemonStarted(Runnable daemonStarted){
      this.daemonStarted = daemonStarted;
    }

    void daemonFinished(Runnable daemonFinished){
      this.daemonFinished = daemonFinished;
    }

    DaemonListener(Disposable disposable, Project project) {
      project.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          daemonStarted.run();
        }

        @Override
        public void daemonFinished() {
          daemonFinished.run();
        }
      });
    }
  }

  private static long calculateMean(List<Long> typingTimings) {
    List<Long> adjustedTimings = ContainerUtil.map(typingTimings, time -> adjustToReferenceTime(time));
    long mean = ArrayUtil.averageAmongMedians(toLongArray(adjustedTimings), 3);
    System.out.println("Intervals: " + typingTimings);
    System.out.println("Adjusted intervals: " + adjustedTimings);
    System.out.println("Mean: " + mean);
    System.out.println(createMetaDataForTeamCity("mean", mean));
    return mean;
  }

  private static String createMetaDataForTeamCity(String name, long value){
    return "##teamcity[testMetadata name='" + name + "' type='number' value='" + value + "']";
  }

  private static List<Integer> findVariableOffsets(PsiFile file, int number) {
    return EdtTestUtil.runInEdtAndGet(() -> {
      return SyntaxTraverser.psiTraverser(file)
        .filter(PsiVariable.class)
        .map((element) -> element.getNameIdentifier().getTextRange().getEndOffset())
        .take(number)
        .toList();
    });
  }

  private static long[] toLongArray(List<Long> list){
    long[] array = new long[list.size()];
    int offset = 0;
    for (long element: list) {
      array[offset++] = element;
    }
    return array;
  }

  private static long adjustToReferenceTime(long time) {
    return time * Timings.REFERENCE_CPU_TIMING / Timings.CPU_TIMING;
  }
}

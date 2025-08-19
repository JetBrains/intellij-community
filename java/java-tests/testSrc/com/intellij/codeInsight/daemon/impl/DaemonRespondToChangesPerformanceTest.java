// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * tests the daemon performance during highlighting interruptions/typing
 */
public class DaemonRespondToChangesPerformanceTest extends DaemonAnalyzerTestCase {
  private static final boolean DEBUG = false;

  public void testHugeAppendChainDoesNotCauseSOE() {
    StringBuilder text = new StringBuilder("class S { String ffffff =  new StringBuilder()\n");
    for (int i=0; i<2000; i++) {
      text.append(".append(").append(i).append(")\n");
    }
    text.append(".toString();<caret>}");
    configureByText(JavaFileType.INSTANCE, text.toString());

    Benchmark.newBenchmark(getName(), () -> {
      List<HighlightInfo> infos = highlightErrors();
      assertEmpty(infos);
      type("k");
      assertNotEmpty(highlightErrors());
      backspace();
    }).start();
  }

  public void testExpressionListsWithManyStringLiteralsHighlightingPerformance() {
    String listBody = StringUtil.join(Collections.nCopies(2000, "\"foo\""), ",\n");
    @Language("JAVA")
    String text = "class S { " +
                  "  String[] s = {" + listBody + "};\n" +
                  "  void foo(String... s) { foo(" + listBody + "); }\n" +
                  "}";
    configureByText(JavaFileType.INSTANCE, text);

    PlatformTestUtil.maskExtensions(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME, getProject(), Collections.emptyList(), getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(LanguageInjector.EXTENSION_POINT_NAME, Collections.emptyList(), getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(new ExtensionPointName<>(LanguageAnnotators.INSTANCE.getName()), Collections.emptyList(), getTestRootDisposable());
    Benchmark.newBenchmark("highlighting many string literals", () -> {
      assertEmpty(highlightErrors());

      type("k");
      assertNotEmpty(highlightErrors());

      backspace();
    }).start();
  }

  public void testPerformanceOfHighlightingLongCallChainWithHierarchyAndGenerics() {
    @Language("JAVA")
    String text = "class Foo { native Foo foo(); }\n" +
                  "class Bar<T extends Foo> extends Foo {\n" +
                  "  native Bar<T> foo();" +
                  "}\n" +
                  "class Goo extends Bar<Goo> {}\n" +
                  "class S { void x(Goo g) { g\n" +
                  StringUtil.repeat(".foo()\n", 2000) +
                  ".toString(); } }";
    configureByText(JavaFileType.INSTANCE, text);

    Benchmark.newBenchmark("highlighting deep call chain", () -> {
      assertEmpty(highlightErrors());

      type("k");
      assertNotEmpty(highlightErrors());

      backspace();
    }).start();
  }

  public void testReactivityPerformance() throws Throwable {
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    type(' ');
    CompletionContributor.forLanguage(getFile().getLanguage());
    highlightErrors();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    int N = Math.max(5, Timings.adjustAccordingToMySpeed(80, false));
    LOG.debug("N = " + N);
    final long[] interruptTimes = new long[N];
    for (int i = 0; i < N; i++) {
      codeAnalyzer.restart(getTestName(false));
      final int finalI = i;
      final long start = System.currentTimeMillis();
      final AtomicLong typingStart = new AtomicLong();
      final AtomicReference<RuntimeException> exception = new AtomicReference<>();
      Future<?> watcher = null;
      try {
        PsiFile psiFile = getFile();
        Editor editor = getEditor();
        Project project = psiFile.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        watcher = ApplicationManager.getApplication().executeOnPooledThread(() -> {
          while (true) {
            Thread.onSpinWait();
            final long start1 = typingStart.get();
            if (start1 == -1) break;
            if (start1 == 0) {
              TimeoutUtil.sleep(5);
              continue;
            }
            long elapsed = System.currentTimeMillis() - start1;
            if (elapsed > 500) {
              // too long, see WTF
              String message = "Too long interrupt: " + elapsed +
                               "; Progress: " + codeAnalyzer.getUpdateProgress() +
                               "\n----------------------------";
              dumpThreadsToConsole();
              exception.set(new RuntimeException(message));
              throw exception.get();
            }
          }
        });
        Runnable interrupt = () -> {
          long now = System.currentTimeMillis();
          if (now - start < 100) {
            // wait to engage all highlighting threads
            return;
          }
          typingStart.set(System.currentTimeMillis());
          type(' ');
          long end = System.currentTimeMillis();
          long interruptTime = end - now;
          interruptTimes[finalI] = interruptTime;
          DaemonProgressIndicator indicator = ContainerUtil.getFirstItem(new ArrayList<>(codeAnalyzer.getUpdateProgress().values()));
          assertTrue(String.valueOf(indicator), indicator == null || indicator.isCanceled());
          LOG.debug("interruptTime:"+interruptTime);
          throw new ProcessCanceledException();
        };
        long hiStart = System.currentTimeMillis();
        codeAnalyzer.runPasses(psiFile, editor.getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, true, interrupt);
        long hiEnd = System.currentTimeMillis();
        DaemonProgressIndicator progress = ContainerUtil.getFirstItem(new ArrayList<>(codeAnalyzer.getUpdateProgress().values()));
        String message = "Should have been interrupted: " + progress + "; Elapsed: " + (hiEnd - hiStart) + "ms";
        dumpThreadsToConsole();
        throw new RuntimeException(message);
      }
      catch (ProcessCanceledException ignored) {
      }
      finally {
        typingStart.set(-1); // cancel watcher
        watcher.get();
        if (exception.get() != null) {
          throw exception.get();
        }
      }
    }

    long ave = ArrayUtil.averageAmongMedians(interruptTimes, 3);
    System.out.println("Average among the N/3 median times: " + ave + "ms");
    assertTrue(ave < 300);
  }

  static void dumpThreadsToConsole() {
    System.err.println("----all threads---");
    for (Thread thread : Thread.getAllStackTraces().keySet()) {

      boolean canceled = CoreProgressManager.isCanceledThread(thread);
      if (canceled) {
        System.err.println("Thread " + thread + " indicator is canceled");
      }
    }
    PerformanceWatcher.dumpThreadsToConsole("");
    System.err.println("----///////---");
  }

  public void testTypingLatencyPerformance() throws Throwable {
    @NonNls String filePath = "/psi/resolve/ThinletBig.java";

    configureByFile(filePath);

    type(' ');
    CompletionContributor.forLanguage(getFile().getLanguage());
    long s = System.currentTimeMillis();
    highlightErrors();
    if (DEBUG) {
      System.out.println("Hi elapsed: "+(System.currentTimeMillis() - s));
    }

    List<String> dumps = new ArrayList<>();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    int N = Math.max(5, Timings.adjustAccordingToMySpeed(80, false));
    LOG.debug("N = " + N);
    final long[] interruptTimes = new long[N];
    for (int i = 0; i < N; i++) {
      codeAnalyzer.restart(getTestName(false));
      final int finalI = i;
      final long start = System.currentTimeMillis();
      Runnable interrupt = () -> {
        long now = System.currentTimeMillis();
        if (now - start < 100) {
          // wait to engage all highlighting threads
          return;
        }
        // set DEBUG=true to see what's causing pauses
        AtomicBoolean finished = new AtomicBoolean();
        if (DEBUG) {
          AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            if (!finished.get()) {
              dumps.add(ThreadDumper.dumpThreadsToString());
            }
          }, 10, TimeUnit.MILLISECONDS);
        }
        type(' ');
        long end = System.currentTimeMillis();
        finished.set(true);
        long interruptTime = end - now;
        interruptTimes[finalI] = interruptTime;
        DaemonProgressIndicator indicator = ContainerUtil.getFirstItem(new ArrayList<>(codeAnalyzer.getUpdateProgress().values()));
        assertTrue(String.valueOf(indicator), indicator == null || indicator.isCanceled());
        throw new ProcessCanceledException();
      };
      try {
        PsiFile psiFile = getFile();
        Editor editor = getEditor();
        Project project = psiFile.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        codeAnalyzer.runPasses(psiFile, editor.getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, true, interrupt);

        throw new RuntimeException("should have been interrupted");
      }
      catch (ProcessCanceledException ignored) {
      }
      backspace();
      //highlightErrors();
    }

    System.out.println("Interrupt times: " + Arrays.toString(interruptTimes));

    if (DEBUG) {
      for (String dump : dumps) {
        System.out.println("\n\n-----------------------------\n\n" + dump);
      }
    }

    long mean = ArrayUtil.averageAmongMedians(interruptTimes, 3);
    long avg = Arrays.stream(interruptTimes).sum() / interruptTimes.length;
    long max = Arrays.stream(interruptTimes).max().getAsLong();
    long min = Arrays.stream(interruptTimes).min().getAsLong();
    System.out.println("Average among the N/3 median times: " + mean + "ms; max: "+max+"; min:"+min+"; avg: "+avg);
    assertTrue(String.valueOf(mean), mean < 10);
  }

  public void testAllPassesFinishAfterInterruptOnTyping_Performance() throws Throwable {
    @NonNls String filePath = "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    highlightErrors();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    type(' ');
    for (int i=0; i<100; i++) {
      backspace();
      codeAnalyzer.restart(getTestName(false));
      try {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();

        PsiFile psiFile = getFile();
        Editor editor = getEditor();
        Project project = psiFile.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        Runnable callbackWhileWaiting = () -> type(' ');
        codeAnalyzer.runPasses(psiFile, editor.getDocument(), textEditor, ArrayUtilRt.EMPTY_INT_ARRAY, true, callbackWhileWaiting);
      }
      catch (ProcessCanceledException ignored) {
        codeAnalyzer.waitForTermination();
        continue;
      }
      fail("PCE must have been thrown");
    }
  }

  // highlights everything at the file level
  static class MyHugeAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    static final AtomicBoolean finished = new AtomicBoolean();
    static final String myText = "blah.MyHugeAnnotator";
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiFile) {
        for (int i=0;i<element.getTextLength();i++) {
          holder.newAnnotation(HighlightSeverity.WARNING, myText).range(new TextRange(i, i+1)).create();
        }
        iDidIt();
        finished.set(true);
      }
    }
  }

  public void testRogueToolGeneratingZillionsOfAnnotationsAtTheSameLevelMustNotFreeze_Performance() {
    int N = 1_000_000;
    configureByText(PlainTextFileType.INSTANCE, " ".repeat(N));
    // just checks that highlighting doesn't freeze because there are no quadratics inside anymore
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(PlainTextLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyHugeAnnotator()}, ()->{
      assertEquals(N, ContainerUtil.count(doHighlighting(), h -> MyHugeAnnotator.myText.equals(h.getDescription())));
      type(' ');
      assertEquals(N+1, ContainerUtil.count(doHighlighting(), h -> MyHugeAnnotator.myText.equals(h.getDescription())));
    });
  }
}

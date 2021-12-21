// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@HardwareAgentRequired
public class DaemonRespondToChangesPerformanceTest extends DaemonAnalyzerTestCase {
  public void testHugeAppendChainDoesNotCauseSOE() {
    StringBuilder text = new StringBuilder("class S { String ffffff =  new StringBuilder()\n");
    for (int i=0; i<2000; i++) {
      text.append(".append(").append(i).append(")\n");
    }
    text.append(".toString();<caret>}");
    configureByText(JavaFileType.INSTANCE, text.toString());

    PlatformTestUtil.startPerformanceTest("highlighting deep call chain", 60_000, () -> {
      List<HighlightInfo> infos = highlightErrors();
      assertEmpty(infos);
      type("k");
      assertNotEmpty(highlightErrors());
      backspace();
    }).usesAllCPUCores().assertTiming();
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
    ExtensionTestUtil.maskExtensions(new ExtensionPointName<>(((ExtensionPointImpl<?>)LanguageAnnotators.INSTANCE.getPoint()).getName()), Collections.emptyList(), getTestRootDisposable());
    PlatformTestUtil.startPerformanceTest("highlighting many string literals", 11_000, () -> {
      assertEmpty(highlightErrors());

      type("k");
      assertNotEmpty(highlightErrors());

      backspace();
    }).usesAllCPUCores().assertTiming();
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

    PlatformTestUtil.startPerformanceTest("highlighting deep call chain", 50_000, () -> {
      assertEmpty(highlightErrors());

      type("k");
      assertNotEmpty(highlightErrors());

      backspace();
    }).usesAllCPUCores().assertTiming();
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
      codeAnalyzer.restart();
      final int finalI = i;
      final long start = System.currentTimeMillis();
      final AtomicLong typingStart = new AtomicLong();
      final AtomicReference<RuntimeException> exception = new AtomicReference<>();
      Future<?> watcher = null;
      try {
        PsiFile file = getFile();
        Editor editor = getEditor();
        Project project = file.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        watcher = ApplicationManager.getApplication().executeOnPooledThread(() -> {
          while (true) {
            final long start1 = typingStart.get();
            if (start1 == -1) break;
            if (start1 == 0) {
              try {
                Thread.sleep(5);
              }
              catch (InterruptedException e1) {
                throw new RuntimeException(e1);
              }
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
          assertTrue(codeAnalyzer.getUpdateProgress().isCanceled());
          System.out.println(interruptTime);
          throw new ProcessCanceledException();
        };
        long hiStart = System.currentTimeMillis();
        codeAnalyzer
          .runPasses(file, editor.getDocument(), Collections.singletonList(textEditor), ArrayUtilRt.EMPTY_INT_ARRAY, false, interrupt);
        long hiEnd = System.currentTimeMillis();
        DaemonProgressIndicator progress = codeAnalyzer.getUpdateProgress();
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

  public static void dumpThreadsToConsole() {
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
    boolean debug = false;

    @NonNls String filePath = "/psi/resolve/ThinletBig.java";

    configureByFile(filePath);

    type(' ');
    CompletionContributor.forLanguage(getFile().getLanguage());
    long s = System.currentTimeMillis();
    highlightErrors();
    if (debug) {
      System.out.println("Hi elapsed: "+(System.currentTimeMillis() - s));
    }

    List<String> dumps = new ArrayList<>();

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    int N = Math.max(5, Timings.adjustAccordingToMySpeed(80, false));
    LOG.debug("N = " + N);
    final long[] interruptTimes = new long[N];
    for (int i = 0; i < N; i++) {
      codeAnalyzer.restart();
      final int finalI = i;
      final long start = System.currentTimeMillis();
      Runnable interrupt = () -> {
        long now = System.currentTimeMillis();
        if (now - start < 100) {
          // wait to engage all highlighting threads
          return;
        }
        // uncomment to debug what's causing pauses
        AtomicBoolean finished = new AtomicBoolean();
        if (debug) {
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
        assertTrue(codeAnalyzer.getUpdateProgress().isCanceled());
        throw new ProcessCanceledException();
      };
      try {
        PsiFile file = getFile();
        Editor editor = getEditor();
        Project project = file.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        codeAnalyzer
          .runPasses(file, editor.getDocument(), Collections.singletonList(textEditor), ArrayUtilRt.EMPTY_INT_ARRAY, false, interrupt);

        throw new RuntimeException("should have been interrupted");
      }
      catch (ProcessCanceledException ignored) {
      }
      backspace();
      //highlightErrors();
    }

    System.out.println("Interrupt times: " + Arrays.toString(interruptTimes));

    if (debug) {
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
      codeAnalyzer.restart();
      try {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();

        PsiFile file = getFile();
        Editor editor = getEditor();
        Project project = file.getProject();
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        Runnable callbackWhileWaiting = () -> type(' ');
        codeAnalyzer.runPasses(file, editor.getDocument(), Collections.singletonList(textEditor), ArrayUtilRt.EMPTY_INT_ARRAY, true, callbackWhileWaiting);
      }
      catch (ProcessCanceledException ignored) {
        codeAnalyzer.waitForTermination();
        continue;
      }
      fail("PCE must have been thrown");
    }
  }
}

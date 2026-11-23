// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture.productionDaemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * tests the daemon performance during highlighting interruptions/typing
 */
@StressTestApplication
public class DaemonRespondToChangesPerfTest {
  private static final Logger LOG = Logger.getInstance(DaemonRespondToChangesPerfTest.class);
  private static final boolean DEBUG = false;
  private static final int FIRST_PASS_START_TIMEOUT_MS = 5_000;
  /** the default autoreparse delay of the IDE */
  private static final int AUTO_REPARSE_DELAY_MS = 300;
  /** the OS key repeat interval of a held key */
  private static final int KEY_REPEAT_MS = 30;
  private static final int HOLD_MS = 1_000;

  private final TestFixture<ProductionDaemonAnalyzerFixture> daemonFixture = productionDaemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  @PerformanceUnitTest
  @Test
  public void testHugeAppendChainDoesNotCauseSOE_Stress() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      StringBuilder text = new StringBuilder(2000*".append(2000)\n".length()+100);
      text.append("class S { String ffffff =  new StringBuilder()\n");
      for (int i=0; i<2000; i++) {
        text.append(".append(").append(i).append(")\n");
      }
      text.append(".toString();<caret>}");
      fixture.configureByText(JavaFileType.INSTANCE, text.toString());

      Benchmark.newBenchmark("testHugeAppendChainDoesNotCauseSOE_Stress", () -> {
        List<HighlightInfo> infos = fixture.waitHighlighting(HighlightSeverity.ERROR);
        assertThat(infos).isEmpty();
        fixture.type("k");
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
        fixture.backspace();
      }).start();
    });
  }

  @PerformanceUnitTest
  @Test
  public void testExpressionListsWithManyStringLiteralsHighlightingPerformance() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      String listBody = StringUtil.join(Collections.nCopies(2000, "\"foo\""), ",\n");
      @Language("JAVA")
      String text = "class S { " +
                    "  String[] s = {" + listBody + "};\n" +
                    "  void foo(String... s) { foo(" + listBody + "); }\n" +
                    "}";

      PlatformTestUtil.maskExtensions(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME, fixture.getProject(), Collections.emptyList(), fixture.getTestRootDisposable());
      ExtensionTestUtil.maskExtensions(LanguageInjector.EXTENSION_POINT_NAME, Collections.emptyList(), fixture.getTestRootDisposable());
      ExtensionTestUtil.maskExtensions(new ExtensionPointName<>(LanguageAnnotators.INSTANCE.getName()), Collections.emptyList(), fixture.getTestRootDisposable());
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Benchmark.newBenchmark("highlighting many string literals", () -> {
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

        fixture.type("k");
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();

        fixture.backspace();
      }).start();
    });
  }

  @PerformanceUnitTest
  @Test
  public void testPerformanceOfHighlightingLongCallChainWithHierarchyAndGenerics() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @Language("JAVA")
      String text = "class Foo { native Foo foo(); }\n" +
                    "class Bar<T extends Foo> extends Foo {\n" +
                    "  native Bar<T> foo();" +
                    "}\n" +
                    "class Goo extends Bar<Goo> {}\n" +
                    "class S { void x(Goo g) { g\n" +
                    StringUtil.repeat(".foo()\n", 2000) +
                    ".toString(); } }";
      fixture.configureByText(JavaFileType.INSTANCE, text);

      Benchmark.newBenchmark("highlighting deep call chain", () -> {
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

        fixture.type("k");
        assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();

        fixture.backspace();
      }).start();
    });
  }

  @PerformanceUnitTest
  @Test
  public void testReactivityPerformance() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @NonNls String filePath = "/psi/resolve/Thinlet.java";
      fixture.configureByFile(filePath);
      fixture.type(' ');
      PsiFile psiFile = fixture.getFile();
      ReadAction.computeBlocking(() -> CompletionContributor.forLanguage(psiFile.getLanguage(), fixture.getEditor()));
      fixture.waitHighlighting(HighlightSeverity.ERROR);

      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      int N = Math.max(5, Timings.adjustAccordingToMySpeed(80, false));
      LOG.debug("N = " + N);
      final long[] interruptTimes = new long[N];
      for (int i = 0; i < N; i++) {
        final int finalI = i;
        EdtTestUtil.runInEdtAndWait(() -> {
          daemonCodeAnalyzer.restart(fixture.getTestName(false));
          final long start = System.currentTimeMillis();
          final AtomicLong typingStart = new AtomicLong();
          final AtomicReference<RuntimeException> exception = new AtomicReference<>();
          Future<?> watcher = null;
          try {
            CodeInsightTestFixtureImpl.ensureIndexesUpToDate(fixture.getProject());
            PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
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
                                   "; Progress: " + daemonCodeAnalyzer.getUpdateProgress() +
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
              fixture.type(' ');
              long end = System.currentTimeMillis();
              long interruptTime = end - now;
              interruptTimes[finalI] = interruptTime;
              DaemonProgressIndicator indicator = ContainerUtil.getFirstItem(new ArrayList<>(daemonCodeAnalyzer.getUpdateProgress().values()));
              assertTrue(indicator == null || indicator.isCanceled(), String.valueOf(indicator));
              LOG.debug("interruptTime:"+interruptTime);
              throw new ProcessCanceledException();
            };
            long hiStart = System.currentTimeMillis();
            testDaemonCodeAnalyzer.waitForDaemonToFinish(fixture.getFile(), interrupt);
            long hiEnd = System.currentTimeMillis();
            DaemonProgressIndicator progress = ContainerUtil.getFirstItem(new ArrayList<>(daemonCodeAnalyzer.getUpdateProgress().values()));
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
        });
      }

      long ave = ArrayUtil.averageAmongMedians(interruptTimes, 3);
      LOG.info("Average among the N/3 median times: " + ave + "ms");
      assertTrue(ave < 300);
    });
  }

  private static void dumpThreadsToConsole() {
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

  @PerformanceUnitTest
  @Test
  public void testTypingLatencyPerformance() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      @NonNls String filePath = "/psi/resolve/ThinletBig.java";

      fixture.configureByFile(filePath);

      fixture.type(' ');
      PsiFile psiFile = fixture.getFile();
      ReadAction.computeBlocking(() -> CompletionContributor.forLanguage(psiFile.getLanguage(), fixture.getEditor()));
      long s = System.currentTimeMillis();
      fixture.waitHighlighting(HighlightSeverity.ERROR);
      if (DEBUG) {
        System.out.println("Hi elapsed: "+(System.currentTimeMillis() - s));
      }

      List<String> dumps = new ArrayList<>();

      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      int N = Math.max(5, Timings.adjustAccordingToMySpeed(80, false));
      LOG.debug("N = " + N);
      final long[] interruptTimes = new long[N];
      for (int i = 0; i < N; i++) {
        final int finalI = i;
        EdtTestUtil.runInEdtAndWait(() -> {
          daemonCodeAnalyzer.restart(fixture.getTestName(false));
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
            fixture.type(' ');
            long end = System.currentTimeMillis();
            finished.set(true);
            long interruptTime = end - now;
            interruptTimes[finalI] = interruptTime;
            DaemonProgressIndicator indicator = ContainerUtil.getFirstItem(new ArrayList<>(daemonCodeAnalyzer.getUpdateProgress().values()));
            assertTrue(indicator == null || indicator.isCanceled(), String.valueOf(indicator));
            throw new ProcessCanceledException();
          };
          try {
            CodeInsightTestFixtureImpl.ensureIndexesUpToDate(fixture.getProject());
            PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
            testDaemonCodeAnalyzer.waitForDaemonToFinish(fixture.getFile(), interrupt);

            throw new RuntimeException("should have been interrupted");
          }
          catch (ProcessCanceledException ignored) {
          }
        });
        fixture.backspace();
        //DaemonRespondToChangesTest.waitHighlighting(getProject(), getEditor().getDocument(), HighlightSeverity.ERROR);
      }

      LOG.debug("Interrupt times: " + Arrays.toString(interruptTimes));

      if (DEBUG) {
        for (String dump : dumps) {
          System.out.println("\n\n-----------------------------\n\n" + dump);
        }
      }

      long mean = ArrayUtil.averageAmongMedians(interruptTimes, 3);
      long avg = Arrays.stream(interruptTimes).sum() / interruptTimes.length;
      long max = Arrays.stream(interruptTimes).max().getAsLong();
      long min = Arrays.stream(interruptTimes).min().getAsLong();
      LOG.info("Average among the N/3 median times: " + mean + "ms; max: "+max+"; min:"+min+"; avg: "+avg);
      assertTrue(mean < 10, String.valueOf(mean));
    });
  }

  @Test
  public void testAllPassesFinishAfterInterruptOnTyping_Stress() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      fixture.configureByFile("/psi/resolve/Thinlet.java");
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isNotEmpty();

      fixture.type(' ');
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      TestDaemonCodeAnalyzerImpl testDaemonCodeAnalyzer = fixture.getTestDaemonCodeAnalyzer();
      for (int i=0; i<100; i++) {
        fixture.backspace();
        boolean canceled = EdtTestUtil.runInEdtAndGet(() -> {
          daemonCodeAnalyzer.restart(fixture.getTestName(false));
          try {
            PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
            CodeInsightTestFixtureImpl.ensureIndexesUpToDate(fixture.getProject());
            Runnable callbackWhileWaiting = () -> fixture.type(' ');
            testDaemonCodeAnalyzer.waitForDaemonToFinish(fixture.getFile(), callbackWhileWaiting);
          }
          catch (ProcessCanceledException ignored) {
            testDaemonCodeAnalyzer.waitForTermination();
            return true;
          }
          return false;
        });
        if (!canceled) {
          fail("PCE must have been thrown");
        }
      }
    });
  }

  /**
   * Opens a big file, and holds a key for {@link #HOLD_MS} while the first pass runs.
   * The first key press cancels the first pass. The daemon must not start a pass again until the key is released,
   * because every start reruns the passes from the dirty scope and the next key repeat cancels it again.
   */
  @PerformanceUnitTest
  @Test
  public void testHeldKeyDoesNotRerunUnfinishedFirstPass() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> TestDaemonCodeAnalyzerImpl.runWithReparseDelay(AUTO_REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          starts.incrementAndGet();
        }
      });
      fixture.configureByFile("/psi/resolve/Thinlet.java");
      CodeInsightTestFixtureImpl.ensureIndexesUpToDate(fixture.getProject());
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      int[] startsWhileHeld = new int[1];
      int[] presses = new int[1];
      EdtTestUtil.runInEdtAndWait(() -> {
        long start = System.currentTimeMillis();
        while (starts.get() == 0) {
          assertTrue(System.currentTimeMillis() - start < FIRST_PASS_START_TIMEOUT_MS, "The first pass must start without the autoreparse delay");
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          TimeoutUtil.sleep(10);
        }
        assertTrue(daemonCodeAnalyzer.isRunning(), "The first pass must run when the key is pressed");

        int startsBefore = starts.get();
        long holdStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - holdStart < HOLD_MS) {
          fireShortcutTriggered();
          presses[0]++;
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          TimeoutUtil.sleep(KEY_REPEAT_MS);
        }
        startsWhileHeld[0] = starts.get() - startsBefore;
      });
      long released = System.currentTimeMillis();
      fixture.waitHighlightingSurviveCancellations(HighlightSeverity.ERROR);
      LOG.info(presses[0] + " key presses in " + HOLD_MS + "ms; daemon starts while the key was held: " + startsWhileHeld[0] +
               "; highlighted " + (System.currentTimeMillis() - released) + "ms after the release");
      assertEquals(0, startsWhileHeld[0], "A held key must not restart the unfinished first pass before the autoreparse delay");
    }));
  }

  /**
   * Publishes the event that {@code IdeKeyEventDispatcher} fires for every key press that maps to an action.
   * The OS key repeat of a held key fires it about 30 times a second.
   */
  static void fireShortcutTriggered() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(AnActionListener.TOPIC)
      .beforeShortcutTriggered(KeyboardShortcut.fromString("DOWN"), List.of(), DataContext.EMPTY_CONTEXT);
  }

  // highlights every character at the file level
  private static class MyHugeAnnotator extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
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

  @Test
  public void testRogueToolGeneratingZillionsOfAnnotationsAtTheSameLevelMustNotFreeze_Stress() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      int N = 20_000;
      // just checks that highlighting doesn't freeze because there are no quadratics inside anymore
      DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(PlainTextLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyHugeAnnotator()}, ()->{
        fixture.configureByText(PlainTextFileType.INSTANCE, " ".repeat(N));
        assertEquals(N, ContainerUtil.count(fixture.waitHighlighting(HighlightSeverity.WARNING), h -> MyHugeAnnotator.myText.equals(h.getDescription())));
        fixture.type(' ');
        assertEquals(N+1, ContainerUtil.count(fixture.waitHighlighting(HighlightSeverity.WARNING), h -> MyHugeAnnotator.myText.equals(h.getDescription())));
      });
    });
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerTestCase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Tests that the first daemon pass of a project starts without the autoreparse delay and that later restarts honor it.
 * A pass over an embedded editor does not count as the first pass.
 * A stop that cancels the unfinished first pass enables the delay, so a held key does not rerun the pass on every press.
 */
public class DaemonFirstPassDelayTest extends ProductionDaemonAnalyzerTestCase {
  private static final int REPARSE_DELAY_MS = 10_000;
  private static final int FIRST_PASS_START_TIMEOUT_MS = 5_000;
  private static final int LATER_RESTART_QUIET_MS = 1_000;

  public void testFirstPassStartsWithoutReparseDelayAndLaterRestartsHonorIt() {
    TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          starts.incrementAndGet();
        }
      });

      // phase 1: the first pass starts long before REPARSE_DELAY_MS
      configureByText(PlainTextFileType.INSTANCE, "text");
      long start = System.currentTimeMillis();
      while (starts.get() == 0) {
        assertTrue("The first pass must start without the autoreparse delay", System.currentTimeMillis() - start < FIRST_PASS_START_TIMEOUT_MS);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        TimeoutUtil.sleep(10);
      }
      // let one pass finish, so the daemon switches to the configured delay
      myTestDaemonCodeAnalyzer.waitHighlightingSurviveCancellations(getFile(), HighlightSeverity.INFORMATION);

      // phase 2: a later restart waits for the delay
      int startsBefore = starts.get();
      myDaemonCodeAnalyzer.restart(getTestName(false));
      long restartTime = System.currentTimeMillis();
      while (System.currentTimeMillis() - restartTime < LATER_RESTART_QUIET_MS) {
        TimeoutUtil.sleep(100);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals("A later restart must wait for the autoreparse delay", startsBefore, starts.get());
        assertFalse("The daemon must not run before the autoreparse delay", myDaemonCodeAnalyzer.isRunning());
      }
      assertTrue("The restart must stay pending until the autoreparse delay expires", myDaemonCodeAnalyzer.isRunningOrPending());
    });
  }

  public void testPassOfEmbeddedEditorDoesNotEnableReparseDelay() {
    TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      AtomicInteger finishes = new AtomicInteger();
      getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          starts.incrementAndGet();
        }

        @Override
        public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
          finishes.incrementAndGet();
        }
      });

      // phase 1: a pass over an embedded editor, like the one of an EditorTextField, finishes
      PsiFile embeddedFile = PsiFileFactory.getInstance(getProject())
        .createFileFromText("embedded.txt", PlainTextFileType.INSTANCE, "text", LocalTimeCounter.currentTime(), true);
      Document embeddedDocument = PsiDocumentManager.getInstance(getProject()).getDocument(embeddedFile);
      assertNotNull(embeddedDocument);
      EditorFactory editorFactory = EditorFactory.getInstance();
      Editor embeddedEditor = editorFactory.createEditor(embeddedDocument, getProject());
      Disposer.register(getTestRootDisposable(), () -> editorFactory.releaseEditor(embeddedEditor));
      EditorTracker.getInstance(getProject()).setActiveEditorsInTests(List.of(embeddedEditor));
      waitUntil(() -> finishes.get() > 0, "The pass over the embedded editor must finish");

      // phase 2: the first pass of a file editor still starts without the delay
      int startsBefore = starts.get();
      configureByText(PlainTextFileType.INSTANCE, "text");
      waitUntil(() -> starts.get() > startsBefore, "The first pass of a file editor must start without the autoreparse delay");
    });
  }

  public void testStopThatCancelsUnfinishedFirstPassEnablesReparseDelay() {
    TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          starts.incrementAndGet();
        }
      });
      AtomicBoolean passStarted = new AtomicBoolean();
      AtomicBoolean passReleased = new AtomicBoolean();
      registerPassThatRunsUntilCanceled(passStarted, passReleased);
      try {
        // phase 1: the first pass starts without the delay and keeps running
        configureByText(PlainTextFileType.INSTANCE, "text");
        waitUntil(passStarted::get, "The first pass must start without the autoreparse delay");

        // phase 2: a key press cancels the unfinished pass, and the restart waits for the delay
        int startsBefore = starts.get();
        DaemonRespondToChangesPerfTest.fireShortcutTriggered();
        assertFalse("The key press must cancel the running pass", myDaemonCodeAnalyzer.isRunning());
        long stopTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - stopTime < LATER_RESTART_QUIET_MS) {
          TimeoutUtil.sleep(100);
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          assertEquals("The restart after a canceled first pass must wait for the autoreparse delay", startsBefore, starts.get());
          assertFalse("The daemon must not run before the autoreparse delay", myDaemonCodeAnalyzer.isRunning());
        }
        assertTrue("The restart must stay pending until the autoreparse delay expires", myDaemonCodeAnalyzer.isRunningOrPending());
      }
      finally {
        passReleased.set(true);
      }
    });
  }

  private void registerPassThatRunsUntilCanceled(@NotNull AtomicBoolean started, @NotNull AtomicBoolean released) {
    TextEditorHighlightingPassFactory factory = (psiFile, editor) -> new EditorBoundHighlightingPass(editor, psiFile, false) {
      @Override
      public void doCollectInformation(@NotNull ProgressIndicator progress) {
        started.set(true);
        while (!released.get()) {
          progress.checkCanceled();
          TimeoutUtil.sleep(10);
        }
      }

      @Override
      public void doApplyInformationToEditor() {
      }
    };
    TextEditorHighlightingPassRegistrar.getInstance(getProject()).registerTextEditorHighlightingPass(factory, null, null, false, -1);
  }

  private static void waitUntil(@NotNull BooleanSupplier condition, @NotNull String message) {
    long start = System.currentTimeMillis();
    while (!condition.getAsBoolean()) {
      assertTrue(message, System.currentTimeMillis() - start < FIRST_PASS_START_TIMEOUT_MS);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      TimeoutUtil.sleep(10);
    }
  }
}

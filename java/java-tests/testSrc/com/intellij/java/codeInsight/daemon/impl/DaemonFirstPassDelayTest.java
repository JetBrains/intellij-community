// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture.productionDaemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the first daemon pass of a project starts without the autoreparse delay and that later restarts honor it.
 * A pass over an embedded editor does not count as the first pass, also when a stop cancels it.
 * A stop that cancels the unfinished first pass enables the delay, so a held key does not rerun the pass on every press.
 * The focus gain of the first editor itself keeps its unfinished first pass, because the pass still covers an active editor.
 * A focus move to another editor still cancels the unfinished pass.
 */
@TestApplication
public class DaemonFirstPassDelayTest {
  private static final int REPARSE_DELAY_MS = 10_000;
  private static final int FIRST_PASS_START_TIMEOUT_MS = 5_000;
  private static final int LATER_RESTART_QUIET_MS = 1_000;

  private final TestFixture<ProductionDaemonAnalyzerFixture> daemonFixture = productionDaemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testFirstPassStartsWithoutReparseDelayAndLaterRestartsHonorIt() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          starts.incrementAndGet();
        }
      });

      // phase 1: the first pass starts long before REPARSE_DELAY_MS
      fixture.configureByText(PlainTextFileType.INSTANCE, "text");
      long start = System.currentTimeMillis();
      EdtTestUtil.runInEdtAndWait(() -> {
        while (starts.get() == 0) {
          assertTrue(System.currentTimeMillis() - start < FIRST_PASS_START_TIMEOUT_MS, "The first pass must start without the autoreparse delay");
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          TimeoutUtil.sleep(10);
        }
      });
      // let one pass finish, so the daemon switches to the configured delay
      fixture.waitHighlightingSurviveCancellations(HighlightSeverity.INFORMATION);

      // phase 2: a later restart waits for the delay
      int startsBefore = starts.get();
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
      EdtTestUtil.runInEdtAndWait(() -> {
        daemonCodeAnalyzer.restart(fixture.getTestName(false));
        long restartTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - restartTime < LATER_RESTART_QUIET_MS) {
          TimeoutUtil.sleep(100);
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
          assertEquals(startsBefore, starts.get(), "A later restart must wait for the autoreparse delay");
          assertFalse(daemonCodeAnalyzer.isRunning(), "The daemon must not run before the autoreparse delay");
        }
        assertTrue(daemonCodeAnalyzer.isRunningOrPending(), "The restart must stay pending until the autoreparse delay expires");
      });
    }));
  }

  @Test
  public void testPassOfEmbeddedEditorDoesNotEnableReparseDelay() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      AtomicInteger finishes = new AtomicInteger();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
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
      Editor embeddedEditor = createEmbeddedEditor(fixture);
      fixture.setActiveEditors(embeddedEditor);
      waitUntil(() -> finishes.get() > 0, "The pass over the embedded editor must finish");

      // phase 2: the first pass of a file editor still starts without the delay
      int startsBefore = starts.get();
      fixture.configureByText(PlainTextFileType.INSTANCE, "text");
      waitUntil(() -> starts.get() > startsBefore, "The first pass of a file editor must start without the autoreparse delay");
    }));
  }

  @Test
  public void testCancelOfEmbeddedEditorPassDoesNotEnableReparseDelay() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      List<FileEditor> started = new CopyOnWriteArrayList<>();
      List<FileEditor> canceled = new CopyOnWriteArrayList<>();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          started.addAll(fileEditors);
        }

        @Override
        public void daemonCanceled(@NotNull String reason, @NotNull Collection<? extends FileEditor> fileEditors) {
          canceled.addAll(fileEditors);
        }
      });
      Editor embeddedEditor = createEmbeddedEditor(fixture);
      AtomicInteger passEntries = new AtomicInteger();
      AtomicBoolean passReleased = new AtomicBoolean();
      registerPassThatRunsUntilCanceled(fixture, editor -> editor == embeddedEditor, passEntries, passReleased);
      try {
        // phase 1: like the Air empty-state editor in the split mode, a pass over an embedded editor keeps running
        fixture.setActiveEditors(embeddedEditor);
        waitUntil(() -> passEntries.get() > 0, "The pass over the embedded editor must start");

        // phase 2: a file editor opens and gains focus, and the stop cancels the running pass of the embedded editor
        fixture.configureByText(PlainTextFileType.INSTANCE, "text");
        Editor editor = fixture.getEditor();
        fixture.setActiveEditors(editor);
        waitUntil(() -> isForEditor(canceled, embeddedEditor), "The active editor change must cancel the pass of the embedded editor");

        // phase 3: the first pass of the file editor still starts without the delay
        waitUntil(() -> isForEditor(started, editor), "The first pass of a file editor must start without the autoreparse delay");
      }
      finally {
        passReleased.set(true);
      }
    }));
  }

  @Test
  public void testStopThatCancelsUnfinishedFirstPassEnablesReparseDelay() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      AtomicInteger starts = new AtomicInteger();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonStarting(@NotNull Collection<? extends FileEditor> fileEditors) {
          starts.incrementAndGet();
        }
      });
      AtomicInteger passEntries = new AtomicInteger();
      AtomicBoolean passReleased = new AtomicBoolean();
      registerMainEditorPassThatRunsUntilCanceled(fixture, passEntries, passReleased);
      try {
        // phase 1: the first pass starts without the delay and keeps running
        fixture.configureByText(PlainTextFileType.INSTANCE, "text");
        waitUntil(() -> passEntries.get() > 0, "The first pass must start without the autoreparse delay");

        // phase 2: a key press cancels the unfinished pass, and the restart waits for the delay
        int startsBefore = starts.get();
        DaemonCodeAnalyzerImpl daemonCodeAnalyzer = fixture.getDaemonCodeAnalyzer();
        EdtTestUtil.runInEdtAndWait(() -> {
          DaemonRespondToChangesPerfTest.fireShortcutTriggered();
          assertFalse(daemonCodeAnalyzer.isRunning(), "The key press must cancel the running pass");
          long stopTime = System.currentTimeMillis();
          while (System.currentTimeMillis() - stopTime < LATER_RESTART_QUIET_MS) {
            TimeoutUtil.sleep(100);
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertEquals(startsBefore, starts.get(), "The restart after a canceled first pass must wait for the autoreparse delay");
            assertFalse(daemonCodeAnalyzer.isRunning(), "The daemon must not run before the autoreparse delay");
          }
          assertTrue(daemonCodeAnalyzer.isRunningOrPending(), "The restart must stay pending until the autoreparse delay expires");
        });
      }
      finally {
        passReleased.set(true);
      }
    }));
  }

  @Test
  public void testFocusGainOfTheFirstEditorItselfKeepsItsUnfinishedFirstPass() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      List<FileEditor> canceled = new CopyOnWriteArrayList<>();
      List<FileEditor> finished = new CopyOnWriteArrayList<>();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonCanceled(@NotNull String reason, @NotNull Collection<? extends FileEditor> fileEditors) {
          canceled.addAll(fileEditors);
        }

        @Override
        public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
          finished.addAll(fileEditors);
        }
      });
      AtomicInteger passEntries = new AtomicInteger();
      AtomicBoolean passReleased = new AtomicBoolean();
      registerMainEditorPassThatRunsUntilCanceled(fixture, passEntries, passReleased);
      try {
        // phase 1: like in the IDE startup, the first pass runs for the selected editor while no editor is active yet
        EdtTestUtil.runInEdtAndWait(() -> {
          fixture.configureByText(PlainTextFileType.INSTANCE, "text");
          fixture.setActiveEditors();
          assertEquals(0, passEntries.get(), "test precondition: the pass must not start before the active editors are cleared");
        });
        waitUntil(() -> passEntries.get() > 0, "The first pass must start for the selected editor while no editor is active");

        // phase 2: the first editor itself gains focus, and its unfinished pass survives
        Editor editor = fixture.getEditor();
        EdtTestUtil.runInEdtAndWait(() -> {
          fixture.setActiveEditors(editor);
          long focusTime = System.currentTimeMillis();
          while (System.currentTimeMillis() - focusTime < LATER_RESTART_QUIET_MS) {
            TimeoutUtil.sleep(100);
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertEquals(1, passEntries.get(), "The focus gain must not restart the running pass");
            assertFalse(isForEditor(canceled, editor), "The focus gain must not cancel the running pass");
          }
        });

        // phase 3: the pass finishes long before the autoreparse delay
        passReleased.set(true);
        waitUntil(() -> isForEditor(finished, editor), "The first pass must finish after the focus gain");
      }
      finally {
        passReleased.set(true);
      }
    }));
  }

  @Test
  public void testFocusMoveToAnotherEditorCancelsTheUnfinishedPass() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> TestDaemonCodeAnalyzerImpl.runWithReparseDelay(REPARSE_DELAY_MS, () -> {
      List<FileEditor> canceled = new CopyOnWriteArrayList<>();
      fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable()).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonCanceled(@NotNull String reason, @NotNull Collection<? extends FileEditor> fileEditors) {
          canceled.addAll(fileEditors);
        }
      });
      AtomicInteger passEntries = new AtomicInteger();
      AtomicBoolean passReleased = new AtomicBoolean();
      registerMainEditorPassThatRunsUntilCanceled(fixture, passEntries, passReleased);
      try {
        // phase 1: the first pass starts without the delay and keeps running
        fixture.configureByText(PlainTextFileType.INSTANCE, "text");
        waitUntil(() -> passEntries.get() > 0, "The first pass must start without the autoreparse delay");

        // phase 2: the focus moves to another editor, and the pass of the editor that left the active set dies
        Editor editor = fixture.getEditor();
        Editor embeddedEditor = createEmbeddedEditor(fixture);
        fixture.setActiveEditors(embeddedEditor);
        waitUntil(() -> isForEditor(canceled, editor), "The focus move to another editor must cancel the pass of the editor that left the active set");
      }
      finally {
        passReleased.set(true);
      }
    }));
  }

  private static @NotNull Editor createEmbeddedEditor(@NotNull ProductionDaemonAnalyzerFixture fixture) {
    return EdtTestUtil.runInEdtAndGet(() -> {
      PsiFile embeddedFile = PsiFileFactory.getInstance(fixture.getProject())
        .createFileFromText("embedded.txt", PlainTextFileType.INSTANCE, "text", LocalTimeCounter.currentTime(), true);
      Document embeddedDocument = PsiDocumentManager.getInstance(fixture.getProject()).getDocument(embeddedFile);
      assertNotNull(embeddedDocument);
      EditorFactory editorFactory = EditorFactory.getInstance();
      Editor embeddedEditor = editorFactory.createEditor(embeddedDocument, fixture.getProject());
      Disposer.register(fixture.getTestRootDisposable(), () -> editorFactory.releaseEditor(embeddedEditor));
      return embeddedEditor;
    });
  }

  private static boolean isForEditor(@NotNull List<FileEditor> fileEditors, @NotNull Editor editor) {
    return ContainerUtil.exists(fileEditors, fileEditor -> fileEditor instanceof TextEditor textEditor && textEditor.getEditor() == editor);
  }

  private static void registerMainEditorPassThatRunsUntilCanceled(@NotNull ProductionDaemonAnalyzerFixture fixture,
                                                                  @NotNull AtomicInteger entries,
                                                                  @NotNull AtomicBoolean released) {
    registerPassThatRunsUntilCanceled(fixture, editor -> editor == fixture.getEditor(), entries, released);
  }

  private static void registerPassThatRunsUntilCanceled(@NotNull ProductionDaemonAnalyzerFixture fixture,
                                                        @NotNull Predicate<? super Editor> isTarget,
                                                        @NotNull AtomicInteger entries,
                                                        @NotNull AtomicBoolean released) {
    TextEditorHighlightingPassFactory factory = (psiFile, editor) -> {
      if (!isTarget.test(editor)) {
        return null;
      }
      return new EditorBoundHighlightingPass(editor, psiFile, false) {
        @Override
        public void doCollectInformation(@NotNull ProgressIndicator progress) {
          entries.incrementAndGet();
          while (!released.get()) {
            progress.checkCanceled();
            TimeoutUtil.sleep(10);
          }
        }

        @Override
        public void doApplyInformationToEditor() {
        }
      };
    };
    TextEditorHighlightingPassRegistrar.getInstance(fixture.getProject()).registerTextEditorHighlightingPass(factory, null, null, false, -1);
  }

  private static void waitUntil(@NotNull BooleanSupplier condition, @NotNull String message) {
    EdtTestUtil.runInEdtAndWait(() -> {
      long start = System.currentTimeMillis();
      while (!condition.getAsBoolean()) {
        assertTrue(System.currentTimeMillis() - start < FIRST_PASS_START_TIMEOUT_MS, message);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        TimeoutUtil.sleep(10);
      }
    });
  }
}

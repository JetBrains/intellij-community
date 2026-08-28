// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.util.PerformanceAssertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression guard for IJPL-251903.
 * <p>
 * {@link PerformanceAssertions#assertDoesNotAffectHighlighting()} must report an error when it is invoked while a
 * highlighting pass is running (i.e. while {@code PassExecutorService.PASS_RUNNING > 0} on the current thread). The
 * assertion had been silently commented out in {@code PerformanceAssertionsImpl.checkDoesNotAffectHighlighting()},
 * so it caught nothing. This test fails if the assertion is weakened or removed again, or if the underlying
 * {@code PASS_RUNNING} bookkeeping in {@code PassExecutorService}/{@code DaemonCodeAnalyzerImpl} stops working.
 */
public class PerformanceAssertionInsideHighlightingPassTest extends DaemonAnalyzerTestCase {
  private static final String EXPECTED_MESSAGE = "the expensive method should not be called inside the highlighting pass";

  public void testAssertReportsErrorWhenCalledInsideHighlightingPass() {
    // A highlighting pass that invokes the assertion from doCollectInformation, i.e. while a pass is running.
    class AssertingPass extends EditorBoundHighlightingPass {
      AssertingPass(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        super(editor, psiFile, false);
      }

      @Override
      public void doCollectInformation(@NotNull ProgressIndicator progress) {
        PerformanceAssertions.assertDoesNotAffectHighlighting();
      }

      @Override
      public void doApplyInformationToEditor() {
      }
    }
    class Fac implements TextEditorHighlightingPassFactory {
      @Override
      public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        return new AssertingPass(editor, psiFile);
      }
    }
    TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(getProject());
    registrar.registerTextEditorHighlightingPass(new Fac(), null, null, false, -1);

    configureByText(PlainTextFileType.INSTANCE, "highlighting performance assertion probe");

    AtomicReference<String> reportedError = new AtomicReference<>();
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
        if (message.contains(EXPECTED_MESSAGE)) {
          reportedError.set(message);
          return Action.NONE; // swallow the expected error so it does not fail the surrounding test
        }
        return super.processError(category, message, details, t);
      }
    }, () -> doHighlighting());

    assertNotNull(
      "PerformanceAssertions.assertDoesNotAffectHighlighting() must report an error when called inside a running highlighting pass. " +
      "If this assertion is null, the check in PerformanceAssertionsImpl.checkDoesNotAffectHighlighting() (IJPL-251903) was disabled, " +
      "or PassExecutorService.PASS_RUNNING is no longer tracked around ScheduledPass.run().",
      reportedError.get());
  }

  public void testAssertIsSilentOutsideHighlightingPass() {
    // Control: outside of a running pass the assertion must NOT report the highlighting error, otherwise the guard
    // above would pass even for a broken "always report" implementation.
    configureByText(PlainTextFileType.INSTANCE, "no pass is running here");

    AtomicReference<String> reportedError = new AtomicReference<>();
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
        if (message.contains(EXPECTED_MESSAGE)) {
          reportedError.set(message);
          return Action.NONE;
        }
        return super.processError(category, message, details, t);
      }
    }, () -> PerformanceAssertions.assertDoesNotAffectHighlighting());

    assertNull("The highlighting performance assertion must not fire outside of a highlighting pass, but it reported: " +
               reportedError.get(),
               reportedError.get());
  }
}

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
import com.intellij.util.PerformanceAssertions;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.TestLoggerKt.assertErrorLogged;
import static com.intellij.testFramework.TestLoggerKt.assertNoErrorLogged;

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
    assertErrorLoggedInHighlightingPass(PerformanceAssertions::assertDoesNotAffectHighlighting);
  }

  public void testSuppressionIsNestedAndScopedInsideHighlightingPass() {
    assertErrorLoggedInHighlightingPass(() -> {
      try (var ignored = PerformanceAssertions.suppressAssertDoesNotAffectHighlighting("IJPL-251903")) {
        PerformanceAssertions.assertDoesNotAffectHighlighting();
        try (var nestedIgnored = PerformanceAssertions.suppressAssertDoesNotAffectHighlighting("IJPL-251903")) {
          PerformanceAssertions.assertDoesNotAffectHighlighting();
        }
        PerformanceAssertions.assertDoesNotAffectHighlighting();
      }
      PerformanceAssertions.assertDoesNotAffectHighlighting();
    });
  }

  public void testAssertIsSilentOutsideHighlightingPass() {
    // Control: outside of a running pass the assertion must NOT report the highlighting error, otherwise the guard
    // above would pass even for a broken "always report" implementation.
    configureByText(PlainTextFileType.INSTANCE, "no pass is running here");

    assertNoErrorLogged(PerformanceAssertions::assertDoesNotAffectHighlighting);
  }

  private void assertErrorLoggedInHighlightingPass(@NotNull Runnable action) {
    runInHighlightingPass(() -> {
      Throwable error = assertErrorLogged(Throwable.class, action::run);
      assertEquals(EXPECTED_MESSAGE, error.getMessage());
    });
  }

  private void runInHighlightingPass(@NotNull Runnable action) {
    class AssertingPass extends EditorBoundHighlightingPass {
      AssertingPass(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        super(editor, psiFile, false);
      }

      @Override
      public void doCollectInformation(@NotNull ProgressIndicator progress) {
        action.run();
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
    doHighlighting();
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BackgroundHighlighter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class IdentifierHighlighterPassFactory {
  private static final Key<Boolean> ourTestingIdentifierHighlighting = Key.create("TestingIdentifierHighlighting");

  public IdentifierHighlighterPass createHighlightingPass(@NotNull PsiFile file,
                                                          @NotNull Editor editor,
                                                          @NotNull TextRange visibleRange) {
    if (CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET &&
        (!editor.isOneLineMode() || !((EditorEx)editor).isEmbeddedIntoDialogWrapper()) &&
        checkDumbMode(file) &&
        isEnabled() &&
        (file.isPhysical() || file.getOriginalFile().isPhysical())) {
      return new IdentifierHighlighterPass(file, editor, visibleRange);
    }
    return null;
  }

  private static boolean checkDumbMode(@NotNull PsiFile file) {
    return !DumbService.isDumb(file.getProject()) || Registry.is("identifier.highlighter.pass.in.dumb.mode");
  }

  public static boolean isEnabled() {
    return !ApplicationManager.getApplication().isUnitTestMode() || TestModeFlags.is(ourTestingIdentifierHighlighting);
  }

  @TestOnly
  @RequiresEdt
  public static void doWithHighlightingEnabled(@NotNull Project project, @NotNull Disposable parentDisposable, @NotNull Runnable r) {
    ThreadingAssertions.assertEventDispatchThread();
    BackgroundHighlighter.Companion.enableListenersInTest(project, parentDisposable);
    try {
      TestModeFlags.runWithFlag(ourTestingIdentifierHighlighting, true, r);
    }
    finally {
      waitForIdentifierHighlighting();
    }
  }

  @TestOnly
  public static void waitForIdentifierHighlighting() {
    // wait for async "highlight identifier" computation to apply in com.intellij.codeInsight.highlighting.BackgroundHighlighter.updateHighlighted
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }
}

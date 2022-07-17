// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BackgroundHighlightingUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestModeFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class IdentifierHighlighterPassFactory {
  private static final Key<Boolean> ourTestingIdentifierHighlighting = Key.create("TestingIdentifierHighlighting");

  public IdentifierHighlighterPass createHighlightingPass(@NotNull PsiFile file,
                                                          @NotNull Editor editor,
                                                          @NotNull TextRange visibleRange) {
    if (!editor.isOneLineMode() &&
        CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET &&
        !DumbService.isDumb(file.getProject()) &&
        isEnabled() &&
        (file.isPhysical() || file.getOriginalFile().isPhysical())) {
      return new IdentifierHighlighterPass(file, editor, visibleRange);
    }

    return null;
  }

  public static boolean isEnabled() {
    return !ApplicationManager.getApplication().isUnitTestMode() || TestModeFlags.is(ourTestingIdentifierHighlighting);
  }

  @TestOnly
  public static void doWithHighlightingEnabled(@NotNull Project project, @NotNull Disposable parentDisposable, @NotNull Runnable r) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    BackgroundHighlightingUtil.enableListenersInTest(project, parentDisposable);
    TestModeFlags.set(ourTestingIdentifierHighlighting, true);
    try {
      r.run();
    }
    finally {
      TestModeFlags.reset(ourTestingIdentifierHighlighting);
      waitForIdentifierHighlighting();
    }
  }

  @TestOnly
  public static void waitForIdentifierHighlighting() {
    // wait for async "highlight identifier" computation to apply in com.intellij.codeInsight.highlighting.BackgroundHighlighter.updateHighlighted
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }
}

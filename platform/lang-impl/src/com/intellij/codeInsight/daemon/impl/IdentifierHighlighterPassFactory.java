// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BackgroundHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class IdentifierHighlighterPassFactory {
  private static final Key<Boolean> ourTestingIdentifierHighlighting = Key.create("TestingIdentifierHighlighting");

  @ApiStatus.Internal
  public boolean shouldHighlightingIdentifiers(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    return CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET &&
           (!editor.isOneLineMode() || !((EditorEx)editor).isEmbeddedIntoDialogWrapper()) &&
           checkDumbMode(psiFile) &&
           isEnabled() &&
           (psiFile.isPhysical() || psiFile.getOriginalFile().isPhysical());
  }

  private static boolean checkDumbMode(@NotNull PsiFile psiFile) {
    return !DumbService.isDumb(psiFile.getProject()) || Registry.is("identifier.highlighter.pass.in.dumb.mode");
  }

  public static boolean isEnabled() {
    return !ApplicationManager.getApplication().isUnitTestMode() || TestModeFlags.is(ourTestingIdentifierHighlighting);
  }

  @TestOnly
  @RequiresEdt
  @ApiStatus.Internal
  public static void doWithIdentifierHighlightingEnabled(@NotNull Project project, @NotNull Runnable r) {
    ThreadingAssertions.assertEventDispatchThread();
    BackgroundHighlighter.Companion.runWithEnabledListenersInTest(project, ()-> {
      try {
        TestModeFlags.runWithFlag(ourTestingIdentifierHighlighting, true, r);
      }
      finally {
        for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
          if (fileEditor instanceof TextEditor te) {
            waitForIdentifierHighlighting(te.getEditor());
          }
        }
      }
    });
  }

  @ApiStatus.Internal
  @TestOnly
  @RequiresEdt
  public static void waitForIdentifierHighlighting(@NotNull Editor editor) {
    Project project = editor.getProject();
    UIUtil.dispatchAllInvocationEvents();
    if (project != null) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }
    UIUtil.dispatchAllInvocationEvents();
    // wait for async "highlight identifier" computation to apply in com.intellij.codeInsight.highlighting.BackgroundHighlighter.updateHighlighted
    BackgroundHighlighter.Companion.waitForIdentifierHighlighting(editor);
  }
}

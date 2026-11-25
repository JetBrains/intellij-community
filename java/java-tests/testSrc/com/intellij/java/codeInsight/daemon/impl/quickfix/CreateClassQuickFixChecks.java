// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The action check of a test of a fix which can create a class in a new file. The fix creates the new file next to the file of the test, and moves
 * the caret into it. Then the {@code after} file holds the text of the new file. When the fix adds the class into the file of
 * the test, the {@code after} file holds the text of the file of the test.
 * <p>
 * A test without a {@code preview} file expects that the preview is the same as the {@code after} file.
 */
final class CreateClassQuickFixChecks {
  private CreateClassQuickFixChecks() {
  }

  /**
   * Checks the action of {@code hint} as {@link LightQuickFixFixture#checkAction} does.
   * {@link LightQuickFixFixture#checkAction} compares a preview without a {@code preview} file with the file of the test, and the result
   * with the file of the test. This check compares them with the new file, when the fix creates one.
   */
  static void checkAction(@NotNull LightQuickFixFixture quickFix, @NotNull String fileName, @NotNull ActionHint hint) {
    JavaCodeInsightTestFixture fixture = quickFix.getFixture();
    IntentionAction action = quickFix.findAction(fileName, hint);
    if (action == null) return;
    String text = action.getText();
    Editor editor = fixture.getEditor();
    PsiElement element = ReadAction.computeBlocking(() -> PsiUtilBase.getElementAtCaret(editor));
    if (hint.shouldCheckPreview()) {
      checkPreviewAndInvoke(fixture, action, Path.of(quickFix.getTestDataPath(), "preview" + fileName));
    }
    else {
      fixture.launchAction(action);
    }
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    if (!quickFix.getOptions().getActionAvailableAfterExecution()) {
      IntentionAction afterAction = CodeInsightTestUtil.findIntentionByText(fixture.getAvailableIntentions(), text);
      if (afterAction != null && Comparing.equal(element, ReadAction.computeBlocking(() -> PsiUtilBase.getElementAtCaret(editor)))) {
        fail("Action '" + text + "' is still available after its invocation in test before" + fileName);
      }
    }
    EdtTestUtil.runInEdtAndWait(() -> NonBlockingReadActionImpl.waitForAsyncTaskCompletion());
    checkResultByFile(fixture, "after" + fileName);
    if (action.getFamilyName().isBlank()) {
      fail("Action '" + text + "' provides empty family name which means that user would see action with empty presentable text in Inspection Results");
    }
  }

  private static void checkPreviewAndInvoke(@NotNull JavaCodeInsightTestFixture fixture, @NotNull IntentionAction action, @NotNull Path path) {
    // Compute the preview in a background thread to catch a write action in the preview
    String previewContent;
    try {
      previewContent = ReadAction.nonBlocking(
          () -> IntentionPreviewPopupUpdateProcessor.getPreviewContent(fixture.getProject(), action, fixture.getFile(), fixture.getEditor()))
        .submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    if (Files.exists(path)) {
      fixture.launchAction(action);
      EdtTestUtil.runInEdtAndWait(() -> NonBlockingReadActionImpl.waitForAsyncTaskCompletion());
      PlatformTestUtil.assertSameLinesWithFile(path.toString(), previewContent);
      return;
    }
    assertFalse(previewContent.isEmpty(), "No preview was generated for '" + action.getText() + "'");
    fixture.launchAction(action);
    EdtTestUtil.runInEdtAndWait(() -> NonBlockingReadActionImpl.waitForAsyncTaskCompletion());
    String expected = EdtTestUtil.runInEdtAndGet(() -> {
      Editor newFileEditor = findNewFileEditor(fixture);
      return newFileEditor == null ? fixture.getFile().getText() : newFileEditor.getDocument().getText();
    });
    assertEquals(expected, previewContent);
  }

  private static void checkResultByFile(@NotNull JavaCodeInsightTestFixture fixture, @NotNull String expectedFilePath) {
    Editor newFileEditor = EdtTestUtil.runInEdtAndGet(() -> findNewFileEditor(fixture));
    if (newFileEditor == null) {
      fixture.checkResultByFile(expectedFilePath);
      return;
    }
    VirtualFile testFile = fixture.getFile().getVirtualFile();
    VirtualFile newFile = FileDocumentManager.getInstance().getFile(newFileEditor.getDocument());
    try {
      EdtTestUtil.runInEdtAndWait(() -> fixture.openFileInEditor(newFile));
      fixture.checkResultByFile(expectedFilePath);
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> {
        fixture.openFileInEditor(testFile);
        deleteNewFile(fixture, newFile);
      });
    }
  }

  /**
   * @return the editor of the file which the fix created, or null when the fix created no file
   */
  private static @Nullable Editor findNewFileEditor(@NotNull JavaCodeInsightTestFixture fixture) {
    Editor editor = FileEditorManager.getInstance(fixture.getProject()).getSelectedTextEditor();
    if (editor == null || editor.getDocument() == fixture.getEditor().getDocument()) return null;
    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    return file != null && file.isValid() ? editor : null;
  }

  /**
   * Deletes the new file, so the next test does not see the class.
   */
  private static void deleteNewFile(@NotNull JavaCodeInsightTestFixture fixture, @NotNull VirtualFile file) {
    FileEditorManager.getInstance(fixture.getProject()).closeFile(file);
    VfsTestUtil.deleteFile(file);
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.daemon.quickFix.QuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A test of a fix which can create a class in a new file. The fix creates the new file next to the file of the test, and moves
 * the caret into it. Then the {@code after} file holds the text of the new file. When the fix adds the class into the file of
 * the test, the {@code after} file holds the text of the file of the test.
 * <p>
 * A test without a {@code preview} file expects that the preview is the same as the {@code after} file.
 */
abstract class CreateClassQuickFixTestCase extends LightQuickFixParameterizedTestCase {
  /**
   * The default wrapper compares a preview without a {@code preview} file with the file of the test. This wrapper compares it
   * with the new file, when the fix creates one. It forwards all other calls to the default wrapper.
   */
  @Override
  protected @NotNull QuickFixTestCase createWrapper(String testDataPath) {
    QuickFixTestCase wrapper = super.createWrapper(testDataPath);
    return (QuickFixTestCase)Proxy.newProxyInstance(
      QuickFixTestCase.class.getClassLoader(), new Class<?>[]{QuickFixTestCase.class}, (proxy, method, args) -> {
        if (method.getName().equals("checkPreviewAndInvoke")) {
          checkPreviewAndInvoke(wrapper, (IntentionAction)args[0], (String)args[1]);
          return null;
        }
        try {
          return method.invoke(wrapper, args);
        }
        catch (InvocationTargetException e) {
          throw e.getCause();
        }
      });
  }

  private void checkPreviewAndInvoke(@NotNull QuickFixTestCase wrapper, @NotNull IntentionAction action, @NotNull String previewFilePath)
    throws Exception {
    if (Files.exists(Path.of(wrapper.getTestDataPath(), previewFilePath))) {
      wrapper.checkPreviewAndInvoke(action, previewFilePath);
      return;
    }
    // Compute the preview in a background thread to catch a write action in the preview
    String previewContent = ReadAction.nonBlocking(
        () -> IntentionPreviewPopupUpdateProcessor.getPreviewContent(getProject(), action, getFile(), getEditor()))
      .submit(AppExecutorUtil.getAppExecutorService()).get();
    assertFalse("No preview was generated for '" + action.getText() + "'", previewContent.isEmpty());
    invoke(action);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    Editor newFileEditor = findNewFileEditor();
    String expected = newFileEditor == null ? getFile().getText() : newFileEditor.getDocument().getText();
    assertEquals(expected, previewContent);
  }

  @Override
  protected void checkResultByFile(@Nullable String message, @NotNull String expectedFilePath, boolean ignoreTrailingSpaces) {
    Editor newFileEditor = findNewFileEditor();
    if (newFileEditor == null) {
      super.checkResultByFile(message, expectedFilePath, ignoreTrailingSpaces);
      return;
    }
    Editor testEditor = getEditor();
    PsiFile testFile = getFile();
    PsiFile newFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(newFileEditor.getDocument());
    assertNotNull(newFile);
    setEditor(newFileEditor);
    setFile(newFile);
    try {
      super.checkResultByFile(message, expectedFilePath, ignoreTrailingSpaces);
    }
    finally {
      setEditor(testEditor);
      setFile(testFile);
      deleteNewFile(newFile.getVirtualFile());
    }
  }

  /**
   * @return the editor of the file which the fix created, or null when the fix created no file
   */
  private @Nullable Editor findNewFileEditor() {
    Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
    if (editor == null || editor.getDocument() == getEditor().getDocument()) return null;
    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    return file != null && file.isValid() ? editor : null;
  }

  /**
   * Deletes the new file, so the next test does not see the class.
   */
  private void deleteNewFile(@NotNull VirtualFile file) {
    FileEditorManager.getInstance(getProject()).closeFile(file);
    VfsTestUtil.deleteFile(file);
  }
}

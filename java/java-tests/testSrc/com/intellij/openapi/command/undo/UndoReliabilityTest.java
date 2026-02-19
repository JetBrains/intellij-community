// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.LoggedError;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;


// IJPL-215217 Undo is broken after Introduce variable action
public class UndoReliabilityTest extends EditorUndoTestCase {

  // com.intellij.openapi.editor.impl.DocumentImpl.UnexpectedBulkUpdateStateException
  private static final Class<RuntimeException> UnexpectedBulkUpdateStateExceptionClass = RuntimeException.class;

  public void test_undo_successful_after_write_bulk_update() {
    runWriteCommandAction(inBulkMode(dummyDocumentChange()));
    undoFirstEditor();
    checkEditorText("");
  }

  public void test_undo_successful_after_transparent_write_bulk_update() {
    runUndoTransparentWriteAction(inBulkMode(dummyDocumentChange()));
    undoFirstEditor();
    checkEditorText("");
  }

  public void test_undo_successful_after_bulk_write_update() {
    // the document is in bulk mode, editor state retrieval fail is expected
    assertErrorLogged(
      UnexpectedBulkUpdateStateExceptionClass,
      inBulkMode(writeCommandAction(dummyDocumentChange()))
    );
    undoFirstEditor();
    checkEditorText("");
  }

  public void test_undo_successful_after_bulk_transparent_write_update() {
    // the document is in bulk mode, editor state retrieval fail is expected
    assertErrorLogged(
      UnexpectedBulkUpdateStateExceptionClass,
      inBulkMode(undoTransparentWriteAction(dummyDocumentChange()))
    );
    undoFirstEditor();
    checkEditorText("");
  }

  public void test_undo_not_available_after_bulk_write_update_with_nonundoable() {
    Document document = getFirstEditor().getDocument();
    DocumentReference docRef = DocumentReferenceManager.getInstance().create(document);
    // the document is in bulk mode, editor state retrieval fail is expected
    assertErrorLogged(
      UnexpectedBulkUpdateStateExceptionClass,
      /* errorCount= */ 4,
      inBulkMode(
        () -> {
          runUndoTransparentWriteAction(dummyDocumentChange());
          myManager.nonundoableActionPerformed(docRef, true);
        }
      )
    );
    assertRedoInFirstEditorNotAvailable();
    checkEditorText(" ");
  }

  private @NotNull Runnable dummyDocumentChange() {
    return () -> getFirstEditor().getDocument().insertString(0, " ");
  }

  private @NotNull Runnable inBulkMode(@NotNull Runnable runnable) {
    return () -> DocumentUtil.executeInBulk(getFirstEditor().getDocument(), runnable);
  }

  private @NotNull Runnable writeCommandAction(@NotNull Runnable runnable) {
    return () -> runWriteCommandAction(runnable);
  }

  private void runWriteCommandAction(@NotNull Runnable runnable) {
    WriteCommandAction.runWriteCommandAction(myProject, runnable);
  }

  private static @NotNull Runnable undoTransparentWriteAction(@NotNull Runnable runnable) {
    return () -> runUndoTransparentWriteAction(runnable);
  }

  private static void runUndoTransparentWriteAction(@NotNull Runnable runnable) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> WriteAction.run(() -> runnable.run()));
  }

  @SuppressWarnings("SameParameterValue")
  private static <T extends Throwable> void assertErrorLogged(@NotNull Class<T> clazz, @NotNull Runnable runnable) {
    TestLoggerKt.assertErrorLogged(clazz, () -> runnable.run());
  }

  @SuppressWarnings("SameParameterValue")
  private static <T extends Throwable> void assertErrorLogged(@NotNull Class<T> clazz, int errorCount, @NotNull Runnable runnable) {
    try {
      assertErrorLogged(clazz, runnable);
    } catch (TestLoggerFactory.TestLoggerAssertionError ex) {
      Throwable[] suppressed = ex.getSuppressed();
      assertSize(errorCount, suppressed);
      for (Throwable throwable : suppressed) {
        assertInstanceOf(throwable, LoggedError.class);
        assertInstanceOf(throwable.getCause(), clazz);
      }
    }
  }
}

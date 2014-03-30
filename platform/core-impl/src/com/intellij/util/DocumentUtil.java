/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import org.jetbrains.annotations.NotNull;

/**
 * Is intended to hold utility methods to use during {@link Document} processing.
 */
public final class DocumentUtil {
  private DocumentUtil() {
  }

  /**
   * Ensures that given task is executed when given document is at the given 'in bulk' mode.
   * 
   * @param document       target document
   * @param executeInBulk  <code>true</code> to force given document to be in bulk mode when given task is executed;
   *                       <code>false</code> to force given document to be <b>not</b> in bulk mode when given task is executed
   * @param task           task to execute
   */
  public static void executeInBulk(@NotNull Document document, final boolean executeInBulk, @NotNull Runnable task) {
    if (!(document instanceof DocumentEx)) {
      task.run();
      return;
    }

    DocumentEx documentEx = (DocumentEx)document;
    if (executeInBulk == documentEx.isInBulkUpdate()) {
      task.run();
      return;
    }

    documentEx.setInBulkUpdate(executeInBulk);
    try {
      task.run();
    }
    finally {
      documentEx.setInBulkUpdate(!executeInBulk);
    }
  }

  public static void writeInRunUndoTransparentAction(@NotNull final Runnable runnable) {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    });
  }

  public static int getFirstNonSpaceCharOffset(@NotNull Document document, int line) {
    int startOffset = document.getLineStartOffset(line);
    int endOffset = document.getLineEndOffset(line);
    return getFirstNonSpaceCharOffset(document, startOffset, endOffset);
  }

  public static int getFirstNonSpaceCharOffset(@NotNull Document document, int startOffset, int endOffset) {
    CharSequence text = document.getImmutableCharSequence();
    for (int i = startOffset; i < endOffset; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t') {
        return i;
      }
    }
    return startOffset;
  }
}

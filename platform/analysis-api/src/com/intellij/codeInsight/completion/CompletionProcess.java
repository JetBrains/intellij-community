// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

/**
 * Represents a completion process from the point of view of the completion framework.
 * It is started when the user types in the editor or invokes completion explicitly (e.g., by a shortcut).
 * It ends when the completion is finished successfully or canceled.
 *
 * @see CompletionService#getCurrentCompletion
 */
public interface CompletionProcess {
  /**
   * @return {@code true} if completion is triggered by typing in the editor, {@code false} if it's invoked by explicit action (e.g., by a shortcut)
   */
  boolean isAutopopupCompletion();
}

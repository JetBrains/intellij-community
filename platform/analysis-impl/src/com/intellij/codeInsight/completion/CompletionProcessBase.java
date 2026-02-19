// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.patterns.ElementPattern;
import org.jetbrains.annotations.NotNull;

public interface CompletionProcessBase extends CompletionProcess {

  /**
   * Add a prefix to be watched for restarting completion.
   *
   * @param startOffset      offset in the document from which the prefix starts.
   * @param restartCondition condition to restart completion when the prefix changes.
   */
  void addWatchedPrefix(int startOffset, @NotNull ElementPattern<String> restartCondition);
}

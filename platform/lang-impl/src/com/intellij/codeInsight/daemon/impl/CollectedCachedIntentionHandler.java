// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

//can be deleted
@ApiStatus.Experimental
@ApiStatus.Internal
public interface CollectedCachedIntentionHandler {

  /**
   * Processes the copy of CachedIntentions for a given file and editor after {@link ShowIntentionsPass#doCollectInformation(ProgressIndicator)}
   *
   * @param editor     the editor in which the intentions are being processed.
   * @param file       the PSI file associated with the intentions.
   * @param intentions the collection of cached intentions to be processed.
   */
  void processCollectedCachedIntentions(@NotNull Editor editor, @NotNull PsiFile file, @NotNull CachedIntentions intentions);
}

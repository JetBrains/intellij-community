// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.injected.editor;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Handles host language specific communications between <b>fragment editor</b> and the host-document.
 *
 * Could be implemented for better injected fragment editing in raw string literals or concatenated strings.
 *
 * @since 2018.3
 */
@ApiStatus.Experimental
public interface InjectedFileChangesHandler {

  boolean isValid();

  void commitToOriginal(@NotNull DocumentEvent injectedDocumentEvent);

  /**
   * @return return true if this handler should be used for the given injected file and text range.
   * Returning true implies that this handler was successfully set up for the given values
   */
  boolean tryReuse(@NotNull PsiFile newInjectedFile, @NotNull TextRange newHostRange);

  /**
   * @return true if given {@code hostRange} corresponds to the injected fragment managed by this handler
   */
  boolean handlesRange(@NotNull TextRange hostRange);
}

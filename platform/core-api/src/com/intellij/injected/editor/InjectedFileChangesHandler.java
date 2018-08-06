// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.injected.editor;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @since 2018.3
 */
@ApiStatus.Experimental
public interface InjectedFileChangesHandler {

  boolean isValid();

  void commitToOriginal(@NotNull DocumentEvent injectedDocumentEvent);

  boolean tryReuse(@NotNull PsiFile newInjectedFile, @NotNull TextRange newHostRange);

  boolean handlesRange(@NotNull TextRange range);
}

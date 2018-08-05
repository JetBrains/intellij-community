// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.injected.editor;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface InjectedFileChangesHandler {

  boolean isValid();

  void commitToOriginal(final DocumentEvent e);

  boolean tryReuse(@NotNull PsiFile injectedFile, TextRange hostRange);

  boolean changesRange(TextRange range);
}

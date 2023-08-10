// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * Used for "goto code block start/end" and "highlight current scope".
 */
public interface CodeBlockProvider {
  @Nullable
  TextRange getCodeBlockRange(Editor editor, PsiFile psiFile);
}

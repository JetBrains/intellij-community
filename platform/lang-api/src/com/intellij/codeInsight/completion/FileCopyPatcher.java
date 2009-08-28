/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class FileCopyPatcher {

  /**
   * On completion, a file copy is created and this method is invoked on corresponding document. This is usually
   * done to ensure that there is some non-whitespace text at caret position, for example, to find reference at
   * that offset and ask for its {@link com.intellij.psi.PsiReference#getVariants()}. In
   * {@link com.intellij.codeInsight.completion.CompletionContributor} it will also be easier to determine which
   * variants to suggest at current position.
   *
   * Default implementation is {@link com.intellij.codeInsight.completion.DummyIdentifierPatcher} which
   * inserts {@link com.intellij.codeInsight.completion.CompletionInitializationContext#DUMMY_IDENTIFIER} 
   * to the document replacing editor selection (see {@link CompletionInitializationContext#START_OFFSET} and
   * {@link CompletionInitializationContext#SELECTION_END_OFFSET}).
   *
   * @param fileCopy
   * @param document
   * @param map {@link com.intellij.codeInsight.completion.CompletionInitializationContext#START_OFFSET} should be valid after return
   */
  public abstract void patchFileCopy(@NotNull final PsiFile fileCopy, @NotNull Document document, @NotNull OffsetMap map);

}

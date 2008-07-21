/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class DummyIdentifierPatcher extends FileCopyPatcher {
  private final String myDummyIdentifier;

  public DummyIdentifierPatcher(final String dummyIdentifier) {
    myDummyIdentifier = dummyIdentifier;
  }

  public void patchFileCopy(@NotNull final PsiFile fileCopy, @NotNull final Document document, @NotNull final OffsetMap map) {
    document.replaceString(map.getOffset(CompletionInitializationContext.START_OFFSET), map.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET),
                           myDummyIdentifier);
  }
}

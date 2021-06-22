// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SerializableInsertHandler {

  /**
   * Tries to serialize this InsertHandler so that it can be applied to a document in a completely different environment.
   * By this moment document should have some prefix typed but no extra edits.
   * Furthermore, document MUST NOT be edited, we can only estimate which changes are to be made.
   * SerializedInsertHandler becomes coupled to the provided LookupElement. May return null because not all the cases are supported.
   * @param element LookupElement to take data from.
   * @param editor editor where completion is performed.
   * @param psiFile psi structure, of the file where completion is happening.
   * @param insertionStart position where current lookupElement is expected to be inserted, i.e caretOffset - prefix
   * @param caretOffset main caretOffset.
   * @return nullable serialized handler.
   */
  @Nullable SerializedInsertHandler trySerialize(@NotNull LookupElement element,
                                                 @NotNull Editor editor,
                                                 @NotNull PsiFile psiFile,
                                                 @NotNull Integer insertionStart,
                                                 @NotNull Integer caretOffset);

  /**
   * Should be invoked to resolve non-caret related side-effects after caret-dependent changes are already applied.
   * @param insertionContext
   * @param element
   */
  void handlePostInsert(@NotNull InsertionContext insertionContext, @NotNull LookupElement element);
}

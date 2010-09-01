/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.smartPointers;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
* User: cdr
*/
class InjectedSelfElementInfo extends SelfElementInfo {
  private DocumentWindow myDocument;

  InjectedSelfElementInfo(@NotNull PsiElement anchor, @NotNull Document document) {
    super(anchor, document);

    assert myFile.getContext() != null;
  }

  protected TextRange getPersistentAnchorRange(final PsiElement anchor, final Document document) {
    final TextRange textRange = super.getPersistentAnchorRange(anchor, document);
    if (!(document instanceof DocumentWindow)) return textRange;
    myDocument = (DocumentWindow)document;
    return myDocument.injectedToHost(textRange);
  }

  protected int getSyncEndOffset() {
    int syncEndOffset = super.getSyncEndOffset();
    return myDocument == null ? syncEndOffset : myDocument.hostToInjected(syncEndOffset);
  }

  protected int getSyncStartOffset() {
    int syncStartOffset = super.getSyncStartOffset();
    return myDocument == null ? syncStartOffset : myDocument.hostToInjected(syncStartOffset);
  }
}

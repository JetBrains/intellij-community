/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.util.TextRange;

public class DocumentFragment {
  private final Document myDocument;
  private final TextRange myTextRange;

  public DocumentFragment(Document document, int startOffset, int endOffset) {
    myDocument = document;
    myTextRange = new TextRange(startOffset, endOffset);
  }

  public Document getDocument() {
    return myDocument;
  }

  public TextRange getTextRange() {
    return myTextRange;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DocumentFragment)) return false;

    final DocumentFragment documentFragment = (DocumentFragment)o;

    if (!myDocument.equals(documentFragment.myDocument)) return false;
    if (!myTextRange.equals(documentFragment.myTextRange)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myDocument.hashCode();
    result = 29 * result + myTextRange.hashCode();
    return result;
  }
}
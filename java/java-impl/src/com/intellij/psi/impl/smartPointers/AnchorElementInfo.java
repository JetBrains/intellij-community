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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* User: cdr
*/
class AnchorElementInfo extends SelfElementInfo {
  AnchorElementInfo(@NotNull PsiElement anchor, PsiFile containingFile) {
    super(anchor, containingFile);
  }

  @Nullable
  public PsiElement restoreElement() {
    if (!mySyncMarkerIsValid) return null;
    PsiFile file = SelfElementInfo.restoreFileFromVirtual(myVirtualFile, myProject);
    if (file == null) return null;
    PsiElement anchor = file.findElementAt(getSyncStartOffset());
    if (anchor == null) return null;

    TextRange range = anchor.getTextRange();
    if (range.getStartOffset() != getSyncStartOffset() || range.getEndOffset() != getSyncEndOffset()) return null;

    if (anchor instanceof PsiIdentifier) {
      PsiElement parent = anchor.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) { // anonymous class, type
        parent = parent.getParent();
      }

      if (!anchor.equals(AnchorElementInfoFactory.getAnchor(parent))) return null;

      return parent;
    }
    if (anchor instanceof XmlToken) {
      XmlToken token = (XmlToken)anchor;
      return token.getTokenType() == XmlTokenType.XML_NAME ? token.getParent() : null;
    }
    return null;
  }
}

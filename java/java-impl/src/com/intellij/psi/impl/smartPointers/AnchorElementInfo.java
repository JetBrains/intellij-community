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

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* User: cdr
*/
class AnchorElementInfo extends SelfElementInfo {
  private int stubId = -1;
  private IStubElementType myStubElementType;

  AnchorElementInfo(@NotNull PsiElement anchor, @NotNull PsiFile containingFile) {
    super(containingFile.getProject(), ProperTextRange.create(anchor.getTextRange()), anchor.getClass(), containingFile,
          containingFile.getLanguage());
    assert !(anchor instanceof PsiFile) : "FileElementInfo must be used for file: "+anchor;
  }
  // will restore by stub index until file tree get loaded
  AnchorElementInfo(@NotNull PsiElement anchor,
                    @NotNull PsiFileWithStubSupport containingFile,
                    int stubId,
                    @NotNull IStubElementType stubElementType) {
    super(containingFile.getProject(), new ProperTextRange(0,0), anchor.getClass(), containingFile, containingFile.getLanguage());
    this.stubId = stubId;
    myStubElementType = stubElementType;
    IElementType contentElementType = ((PsiFileImpl)containingFile).getContentElementType();
    assert contentElementType instanceof IStubFileElementType : contentElementType;
    assert !(anchor instanceof PsiFile) : "FileElementInfo must be used for file: "+anchor;
  }

  @Override
  @Nullable
  public PsiElement restoreElement() {
    if (stubId != -1) {
      PsiFile file = restoreFile();
      if (!(file instanceof PsiFileWithStubSupport)) return null;
      return PsiAnchor.restoreFromStubIndex((PsiFileWithStubSupport)file, stubId, myStubElementType, false);
    }
    if (!mySyncMarkerIsValid) return null;
    PsiFile file = restoreFile();
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

  @Override
  public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
    if (other instanceof AnchorElementInfo) {
      AnchorElementInfo otherAnchor = (AnchorElementInfo)other;
      if (stubId != -1 && otherAnchor.stubId != -1 && stubId != otherAnchor.stubId) return false;
      if (myStubElementType != null && otherAnchor.myStubElementType != null && myStubElementType != otherAnchor.myStubElementType) return false;
    }
    return super.pointsToTheSameElementAs(other);
  }

  @Override
  public void fastenBelt(int offset, RangeMarker[] cachedRangeMarker) {
    if (stubId != -1) {
      PsiElement element = restoreElement();
      if (element != null) {
        // switch to tree
        stubId = -1;
        myStubElementType = null;
        PsiElement anchor = AnchorElementInfoFactory.getAnchor(element);
        setRange((anchor == null ? element : anchor).getTextRange());
      }
    }
    super.fastenBelt(offset, cachedRangeMarker);
  }
}

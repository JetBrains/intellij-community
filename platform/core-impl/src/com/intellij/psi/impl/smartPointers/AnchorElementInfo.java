/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* User: cdr
*/
class AnchorElementInfo extends SelfElementInfo {
  private volatile long myStubElementTypeAndId; // stubId in the lower 32 bits; stubElementTypeIndex in the high 32 bits packed together for atomicity

  AnchorElementInfo(@NotNull PsiElement anchor, @NotNull PsiFile containingFile) {
    super(containingFile.getProject(), ProperTextRange.create(anchor.getTextRange()), anchor.getClass(), containingFile, LanguageUtil.getRootLanguage(
      anchor));
    assert !(anchor instanceof PsiFile) : "FileElementInfo must be used for file: "+anchor;
    myStubElementTypeAndId = pack(-1, null);
  }
  // will restore by stub index until file tree get loaded
  AnchorElementInfo(@NotNull PsiElement anchor,
                    @NotNull PsiFileWithStubSupport containingFile,
                    int stubId,
                    @NotNull IStubElementType stubElementType) {
    super(containingFile.getProject(), new ProperTextRange(0, 0), anchor.getClass(), containingFile, containingFile.getLanguage());
    myStubElementTypeAndId = pack(stubId, stubElementType);
    assert !(anchor instanceof PsiFile) : "FileElementInfo must be used for file: "+anchor;
  }

  private static long pack(int stubId, IStubElementType stubElementType) {
    short index = stubElementType == null ? 0 : stubElementType.getIndex();
    assert index >= 0 : "Unregistered token types not allowed here: " + stubElementType;
    return ((long)stubId) | ((long)index << 32);
  }

  private int getStubId() {
    return (int)myStubElementTypeAndId;
  }

  @Override
  @Nullable
  public PsiElement restoreElement() {
    long typeAndId = myStubElementTypeAndId;
    int stubId = (int)typeAndId;
    if (stubId != -1) {
      PsiFile file = restoreFile();
      if (!(file instanceof PsiFileWithStubSupport)) return null;
      short index = (short)(typeAndId >> 32);
      IStubElementType stubElementType = (IStubElementType)IElementType.find(index);
      return PsiAnchor.restoreFromStubIndex((PsiFileWithStubSupport)file, stubId, stubElementType, false);
    }
    if (!mySyncMarkerIsValid) return null;
    PsiFile file = restoreFile();
    if (file == null) return null;
    PsiElement anchor = file.findElementAt(getSyncStartOffset());
    if (anchor == null) return null;

    TextRange range = anchor.getTextRange();
    if (range.getStartOffset() != getSyncStartOffset() || range.getEndOffset() != getSyncEndOffset()) return null;

    for (SmartPointerAnchorProvider provider : SmartPointerAnchorProvider.EP_NAME.getExtensions()) {
      final PsiElement element = provider.restoreElement(anchor);
      if (element != null) return element;
    }
    return null;
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull final SmartPointerElementInfo other) {
    if (other instanceof AnchorElementInfo) {
      AnchorElementInfo otherAnchor = (AnchorElementInfo)other;
      if ((getStubId() == -1) != (otherAnchor.getStubId() == -1)) {
        return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            return Comparing.equal(restoreElement(), other.restoreElement());
          }
        });
      }
      if (myStubElementTypeAndId != otherAnchor.myStubElementTypeAndId) return false;
    }
    return super.pointsToTheSameElementAs(other);
  }

  @Override
  public void fastenBelt(int offset, RangeMarker[] cachedRangeMarker) {
    if (getStubId() != -1) {
      switchToTree();
    }
    super.fastenBelt(offset, cachedRangeMarker);
  }

  private void switchToTree() {
    PsiElement element = restoreElement();
    if (element != null) {
      // switch to tree
      myStubElementTypeAndId = pack(-1, null);
      PsiElement anchor = AnchorElementInfoFactory.getAnchor(element);
      setRange((anchor == null ? element : anchor).getTextRange());
    }
  }

  @Override
  public Segment getRange() {
    if (getStubId() != -1) {
      switchToTree();
    }
    return super.getRange();
  }

}

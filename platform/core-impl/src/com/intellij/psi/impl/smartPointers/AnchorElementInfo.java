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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AnchorElementInfo extends SelfElementInfo {
  private volatile long myStubElementTypeAndId; // stubId in the lower 32 bits; stubElementTypeIndex in the high 32 bits packed together for atomicity

  AnchorElementInfo(@NotNull PsiElement anchor, @NotNull PsiFile containingFile, @NotNull Identikit.ByAnchor identikit) {
    super(ProperTextRange.create(anchor.getTextRange()), identikit, containingFile, false);
    myStubElementTypeAndId = pack(-1, null);
  }
  // will restore by stub index until file tree get loaded
  AnchorElementInfo(@NotNull PsiElement anchor,
                    @NotNull PsiFileWithStubSupport containingFile,
                    int stubId,
                    @NotNull IStubElementType stubElementType) {
    super(null,
          Identikit.fromTypes(anchor.getClass(), stubElementType, LanguageUtil.getRootLanguage(containingFile)),
          containingFile, false);
    myStubElementTypeAndId = pack(stubId, stubElementType);
    assert !(anchor instanceof PsiFile) : "FileElementInfo must be used for file: "+anchor;
  }

  private static long pack(int stubId, @Nullable IStubElementType stubElementType) {
    short index = stubElementType == null ? 0 : stubElementType.getIndex();
    assert index >= 0 : "Unregistered token types not allowed here: " + stubElementType;
    return ((long)stubId) | ((long)index << 32);
  }

  private int getStubId() {
    return (int)myStubElementTypeAndId;
  }

  @Override
  @Nullable
  public PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager) {
    long typeAndId = myStubElementTypeAndId;
    int stubId = (int)typeAndId;
    if (stubId != -1) {
      PsiFile file = restoreFile(manager);
      if (!(file instanceof PsiFileWithStubSupport)) return null;
      short index = (short)(typeAndId >> 32);
      IStubElementType stubElementType = (IStubElementType)IElementType.find(index);
      return PsiAnchor.restoreFromStubIndex((PsiFileWithStubSupport)file, stubId, stubElementType, false);
    }

    return super.restoreElement(manager);
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull final SmartPointerElementInfo other, @NotNull SmartPointerManagerImpl manager) {
    if (other instanceof AnchorElementInfo) {
      if (!getVirtualFile().equals(other.getVirtualFile())) return false;

      long packed1 = myStubElementTypeAndId;
      long packed2 = ((AnchorElementInfo)other).myStubElementTypeAndId;

      if (packed1 != -1 && packed2 != -1) {
        return packed1 == packed2;
      }
      if (packed1 != -1 || packed2 != -1) {
        return ReadAction.compute(() -> Comparing.equal(restoreElement(manager), other.restoreElement(manager)));
      }
    }
    return super.pointsToTheSameElementAs(other, manager);
  }

  @Override
  public void fastenBelt(@NotNull SmartPointerManagerImpl manager) {
    if (getStubId() != -1) {
      switchToTree(manager);
    }
    super.fastenBelt(manager);
  }

  private void switchToTree(@NotNull SmartPointerManagerImpl manager) {
    PsiElement element = restoreElement(manager);
    SmartPointerTracker tracker = manager.getTracker(getVirtualFile());
    if (element != null && tracker != null) {
      tracker.switchStubToAst(this, element);
    }
  }

  void switchToTreeRange(@NotNull PsiElement element) {
    switchToAnchor(element);
    myStubElementTypeAndId = pack(-1, null);
  }

  @Override
  public Segment getRange(@NotNull SmartPointerManagerImpl manager) {
    if (getStubId() != -1) {
      switchToTree(manager);
    }
    return super.getRange(manager);
  }

  @Nullable
  @Override
  public TextRange getPsiRange(@NotNull SmartPointerManagerImpl manager) {
    if (getStubId() != -1) {
      switchToTree(manager);
    }
    return super.getPsiRange(manager);
  }

  @Override
  public String toString() {
    return super.toString() + ",stubId=" + getStubId();
  }
}

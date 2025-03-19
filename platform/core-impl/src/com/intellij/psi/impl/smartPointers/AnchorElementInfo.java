// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
                    @NotNull IElementType stubElementType) {
    super(null,
          Identikit.fromTypes(anchor.getClass(), stubElementType, LanguageUtil.getRootLanguage(containingFile)),
          containingFile, false);
    myStubElementTypeAndId = pack(stubId, stubElementType);
    assert !(anchor instanceof PsiFile) : "FileElementInfo must be used for file: "+anchor;
  }

  private static long pack(int stubId, @Nullable IElementType stubElementType) {
    short index = stubElementType == null ? 0 : stubElementType.getIndex();
    assert index >= 0 : "Unregistered token types not allowed here: " + stubElementType;
    return ((long)stubId) | ((long)index << 32);
  }

  private int getStubId() {
    return (int)myStubElementTypeAndId;
  }

  @Override
  public @Nullable PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager) {
    long typeAndId = myStubElementTypeAndId;
    int stubId = (int)typeAndId;
    if (stubId != -1) {
      PsiFile file = restoreFile(manager);
      if (!(file instanceof PsiFileWithStubSupport)) return null;
      short index = (short)(typeAndId >> 32);
      IElementType stubElementType = IElementType.find(index);
      return PsiAnchor.restoreFromStubIndex((PsiFileWithStubSupport)file, stubId, stubElementType, false);
    }

    return super.restoreElement(manager);
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other, @NotNull SmartPointerManagerImpl manager) {
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

  @Override
  public @Nullable TextRange getPsiRange(@NotNull SmartPointerManagerImpl manager) {
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

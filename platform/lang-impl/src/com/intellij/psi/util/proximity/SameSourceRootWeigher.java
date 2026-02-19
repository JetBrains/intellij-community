// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SameSourceRootWeigher extends ProximityWeigher {
  private static final NullableLazyKey<VirtualFile, ProximityLocation> SOURCE_ROOT_KEY = NullableLazyKey.create("sourceRoot",
                                                                                                                 proximityLocation -> findSourceRoot(proximityLocation.getPosition()));

  @Override
  public Comparable weigh(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    if (location.getPosition() == null){
      return null;
    }
    final VirtualFile sourceRoot = SOURCE_ROOT_KEY.getValue(location);
    if (sourceRoot == null) {
      return false;
    }

    return sourceRoot.equals(findSourceRoot(element));
  }

  private static VirtualFile findSourceRoot(PsiElement element) {
    if (element == null) return null;

    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return null;

    final VirtualFile file = psiFile.getOriginalFile().getVirtualFile();
    if (file == null) return null;

    return ProjectFileIndex.getInstance(element.getProject()).getSourceRootForFile(file);
  }
}
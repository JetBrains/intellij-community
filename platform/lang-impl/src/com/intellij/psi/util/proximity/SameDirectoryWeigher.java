// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class SameDirectoryWeigher extends ProximityWeigher {
  private static final NullableLazyKey<PsiDirectory, ProximityLocation>
    PLACE_DIRECTORY = NullableLazyKey.create("placeDirectory", location -> getParentDirectory(location.getPosition()));

  private static PsiDirectory getParentDirectory(PsiElement element) {
    PsiFile file = InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element);
    if (file != null) {
      element = file.getOriginalFile();
    }
    while (element != null && !(element instanceof PsiDirectory)) {
      element = element.getParent();
    }
    return (PsiDirectory)element;
  }

  @Override
  public Boolean weigh(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    if (location.getPosition() == null) {
      return Boolean.TRUE;
    }
    final PsiDirectory placeDirectory = PLACE_DIRECTORY.getValue(location);
    return placeDirectory != null && placeDirectory.equals(getParentDirectory(element));
  }
}

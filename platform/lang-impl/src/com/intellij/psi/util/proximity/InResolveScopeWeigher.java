// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class InResolveScopeWeigher extends ProximityWeigher {
  private static final NotNullLazyKey<GlobalSearchScope, ProximityLocation> PLACE_SCOPE = NotNullLazyKey.createLazyKey("placeScope", location -> {
    PsiElement position = location.getPosition();
    return position == null ? GlobalSearchScope.EMPTY_SCOPE : position.getResolveScope();
  });


  @Override
  public Comparable weigh(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    VirtualFile elementFile = PsiUtilCore.getVirtualFile(element);
    return elementFile != null && PLACE_SCOPE.getValue(location).contains(elementFile);
  }
}

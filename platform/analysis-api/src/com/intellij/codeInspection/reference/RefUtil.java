// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Application component which provides utility methods for working with the reference
 * graph.
 *
 * @author anna
 */
public final class RefUtil {
  private RefUtil() {}

  public static boolean isImplicitUsage(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      if (provider.isImplicitUsage(element)) return true;
    }
    return false;
  }

  public static boolean isImplicitRead(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      if (provider.isImplicitRead(element)) return true;
    }
    return false;
  }

  public static boolean contains(RefEntity element, @NotNull Collection<? extends RefEntity> entities){
    for (RefEntity refEntity : entities) {
      if (Comparing.equal(refEntity, element)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isImplicitWrite(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      if (provider.isImplicitWrite(element)) return true;
    }
    return EntryPointsManager.getInstance(element.getProject()).isImplicitWrite(element);
  }
}

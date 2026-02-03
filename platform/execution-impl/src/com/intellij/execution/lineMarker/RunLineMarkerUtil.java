// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RunLineMarkerUtil {
  @ApiStatus.Internal
  public static boolean hasAnyLineMarkerInfo(@NotNull PsiElement element) {
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
    if (injectedLanguageManager.isInjectedFragment(element.getContainingFile())) return false;
    List<RunLineMarkerContributor> contributors = RunLineMarkerContributor.EXTENSION.allForLanguageOrAny(element.getLanguage());
    for (RunLineMarkerContributor contributor : contributors) {
      if (contributor.getInfo(element) != null || contributor.getSlowInfo(element) != null) {
        return true;
      }
    }
    return false;
  }
}

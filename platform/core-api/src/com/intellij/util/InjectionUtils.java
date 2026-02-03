// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class InjectionUtils {
  private static final Key<Boolean> INJECT_LANGUAGE_ACTION_ENABLED_FOR_HOST = Key.create("inject language action enabled for host");
  public static void enableInjectLanguageAction(@NotNull PsiElement host, boolean enabled) {
    host.putUserData(INJECT_LANGUAGE_ACTION_ENABLED_FOR_HOST, enabled);
  }
  public static boolean isInjectLanguageActionEnabled(@NotNull PsiElement host) {
    return !Boolean.FALSE.equals(host.getUserData(INJECT_LANGUAGE_ACTION_ENABLED_FOR_HOST));
  }

  private static final Key<Boolean> INSPECT_INJECTED_FILES = Key.create("run inspections for files injected into this PSI file");
  public static void setInspectInjectedFiles(@NotNull PsiFile topLevelFile, boolean enabled) {
    topLevelFile.putUserData(INSPECT_INJECTED_FILES, enabled);
  }
  public static boolean shouldInspectInjectedFiles(@NotNull PsiFile file) {
    return !Boolean.FALSE.equals(file.getUserData(INSPECT_INJECTED_FILES));
  }

  private static final Key<Boolean> COLLECT_LINE_MARKERS_FOR_INJECTED_FILES = Key.create("collect line markers for files injected into this PSI file");
  public static void setCollectLineMarkersForInjectedFiles(@NotNull PsiFile topLevelFile, boolean enabled) {
    topLevelFile.putUserData(COLLECT_LINE_MARKERS_FOR_INJECTED_FILES, enabled);
  }
  public static boolean shouldCollectLineMarkersForInjectedFiles(@NotNull PsiFile file) {
    return !Boolean.FALSE.equals(file.getUserData(COLLECT_LINE_MARKERS_FOR_INJECTED_FILES));
  }

  private static final Key<Boolean>
    DO_POSTPROCESS_FORMATTING_ONLY_FOR_INJECTED_FRAGMENTS = Key.create("create reformatting actions only for PSI elements injected into this host");
  public static void setFormatOnlyInjectedCode(@NotNull FileViewProvider topLevelViewProvider, boolean enabled) {
    topLevelViewProvider.putUserData(DO_POSTPROCESS_FORMATTING_ONLY_FOR_INJECTED_FRAGMENTS, enabled);
  }
  public static boolean shouldFormatOnlyInjectedCode(@NotNull FileViewProvider topLevelViewProvider) {
    return Boolean.TRUE.equals(topLevelViewProvider.getUserData(DO_POSTPROCESS_FORMATTING_ONLY_FOR_INJECTED_FRAGMENTS));
  }
}

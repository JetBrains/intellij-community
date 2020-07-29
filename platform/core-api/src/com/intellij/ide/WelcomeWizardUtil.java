// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WelcomeWizardUtil {
  private static volatile String ourDefaultLAF;
  private static volatile String ourWizardLAF;
  private static volatile String ourWizardMacKeymap;
  private static volatile String ourWizardEditorScheme;
  private static volatile Boolean ourAutoScrollToSource;
  private static volatile Integer ourCompletionCaseSensitive;
  private static volatile Boolean ourManualOrder;
  private static volatile Integer ourTabsPlacement;
  private static volatile Integer ourContinuationIndent;
  private static volatile Integer ourAppearanceFontSize;
  private static volatile String ourAppearanceFontFace;
  private static volatile Boolean ourDisableBreakpointsOnClick;
  private static final Set<String> ourFeaturedPluginsToInstall = new HashSet<>();

  public static void setDefaultLAF(String laf) {
    ourDefaultLAF = laf;
  }

  public static String getDefaultLAF() {
    return ourDefaultLAF;
  }

  public static void setWizardLAF(String laf) {
    ourWizardLAF = laf;
  }

  public static String getWizardLAF() {
    return ourWizardLAF;
  }

  public static void setWizardKeymap(@Nullable String keymap) {
    ourWizardMacKeymap = keymap;
  }

  @Nullable
  public static String getWizardMacKeymap() {
    return ourWizardMacKeymap;
  }

  public static void setWizardEditorScheme(@Nullable String wizardEditorScheme) {
    ourWizardEditorScheme = wizardEditorScheme;
  }

  @Nullable
  public static String getWizardEditorScheme() {
    return ourWizardEditorScheme;
  }

  @Nullable
  public static Boolean getAutoScrollToSource() {
    return ourAutoScrollToSource;
  }

  public static void setAutoScrollToSource(@Nullable Boolean autoScrollToSource) {
    ourAutoScrollToSource = autoScrollToSource;
  }

  public static Set<String> getFeaturedPluginsToInstall() {
    return Collections.unmodifiableSet(ourFeaturedPluginsToInstall);
  }

  public static void setFeaturedPluginsToInstall(Set<String> pluginsToInstall) {
    ourFeaturedPluginsToInstall.clear();
    ourFeaturedPluginsToInstall.addAll(pluginsToInstall);
  }

  public static Boolean getDisableBreakpointsOnClick() {
    return ourDisableBreakpointsOnClick;
  }

  public static void setDisableBreakpointsOnClick(Boolean disableBreakpointsOnClick) {
    ourDisableBreakpointsOnClick = disableBreakpointsOnClick;
  }

  public static void setCompletionCaseSensitive(Integer completionCaseSensitive) {
    ourCompletionCaseSensitive = completionCaseSensitive;
  }

  public static Integer getCompletionCaseSensitive() {
    return ourCompletionCaseSensitive;
  }

  public static Boolean getManualOrder() {
    return ourManualOrder;
  }

  public static void setManualOrder(Boolean manualOrder) {
    ourManualOrder = manualOrder;
  }

  public static void setTabsPlacement(Integer tabsPlacement) {
    ourTabsPlacement = tabsPlacement;
  }

  public static Integer getTabsPlacement() {
    return ourTabsPlacement;
  }

  public static void setContinuationIndent(Integer continuationIndent) {
    ourContinuationIndent = continuationIndent;
  }

  public static Integer getContinuationIndent() {
    return ourContinuationIndent;
  }

  public static Integer getAppearanceFontSize() {
    return ourAppearanceFontSize;
  }

  public static void setAppearanceFontSize(Integer appearanceFontSize) {
    ourAppearanceFontSize = appearanceFontSize;
  }

  public static String getAppearanceFontFace() {
    return ourAppearanceFontFace;
  }

  public static void setAppearanceFontFace(String appearanceFontFace) {
    ourAppearanceFontFace = appearanceFontFace;
  }
}

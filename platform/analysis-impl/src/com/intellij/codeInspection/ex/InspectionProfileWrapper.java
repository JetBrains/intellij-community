// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class InspectionProfileWrapper {
  private static final Logger LOG = Logger.getInstance(InspectionProfileWrapper.class);

  /**
   * Key that is assumed to hold strategy that customizes {@link InspectionProfileWrapper} object to use.
   * <p/>
   * I.e. given strategy (if any) receives {@link InspectionProfileWrapper} object that is going to be used so far and returns
   * {@link InspectionProfileWrapper} object that should be used later.
   */
  private static final Key<Function<? super InspectionProfile, ? extends InspectionProfileWrapper>> CUSTOMIZATION_KEY = Key.create("Inspection Profile Wrapper Customization");
  public static final Key<Map<Class<? extends PsiElement>, Set<PsiElement>>> PSI_ELEMENTS_BEING_COMMITTED = Key.create("PsiElements that are being committed");

  // check whether some inspection got registered twice by accident. 've bit once.
  private static boolean alreadyChecked;

  protected final InspectionProfile myProfile;
  protected final InspectionProfileManager myProfileManager;

  public InspectionProfileWrapper(@NotNull InspectionProfileImpl profile) {
    myProfile = profile;
    myProfileManager = profile.getProfileManager();
  }

  public InspectionProfileWrapper(@NotNull InspectionProfile profile,
                                  @NotNull InspectionProfileManager profileManager) {
    myProfile = profile;
    myProfileManager = profileManager;
  }

  public InspectionProfileManager getProfileManager() {
    return myProfileManager;
  }

  public static void checkInspectionsDuplicates(@NotNull List<? extends InspectionToolWrapper<?, ?>> toolWrappers) {
    if (alreadyChecked) {
      return;
    }

    alreadyChecked = true;
    Set<InspectionProfileEntry> uniqueTools = new HashSet<>(toolWrappers.size());
    for (InspectionToolWrapper<?, ?> toolWrapper : toolWrappers) {
      ProgressManager.checkCanceled();
      if (!uniqueTools.add(toolWrapper.getTool())) {
        LOG.error("Inspection " + toolWrapper.getDisplayName() + " (" + toolWrapper.getTool().getClass() + ") already registered");
      }
    }
  }

  public boolean isToolEnabled(@Nullable HighlightDisplayKey key, PsiElement element) {
    return myProfile.isToolEnabled(key, element);
  }

  public @NotNull HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
    return myProfile.getErrorLevel(inspectionToolKey, element);
  }

  public InspectionToolWrapper<?, ?> getInspectionTool(@NotNull String shortName, @Nullable PsiElement element) {
    return myProfile.getInspectionTool(shortName, element);
  }

  public @NotNull InspectionProfile getInspectionProfile() {
    return myProfile;
  }

  public static void runWithCustomInspectionWrapper(@NotNull PsiFile file, @NotNull Function<? super InspectionProfile, ? extends InspectionProfileWrapper> customizer, @NotNull Runnable runnable) {
    file.putUserData(CUSTOMIZATION_KEY, customizer);
    try {
      runnable.run();
    }
    finally {
      file.putUserData(CUSTOMIZATION_KEY, null);
    }
  }

  public static Function<? super InspectionProfile, ? extends InspectionProfileWrapper> getCustomInspectionProfileWrapper(@NotNull PsiFile file) {
    return file.getUserData(CUSTOMIZATION_KEY);
  }

  /**
   * @deprecated use more structured {@link #runWithCustomInspectionWrapper(PsiFile, Function, Runnable)} instead
   */
  @Deprecated
  public static void setCustomInspectionProfileWrapperTemporarily(@NotNull PsiFile file, @NotNull Function<? super InspectionProfile, ? extends InspectionProfileWrapper> function) {
    file.putUserData(CUSTOMIZATION_KEY, function);
  }
}

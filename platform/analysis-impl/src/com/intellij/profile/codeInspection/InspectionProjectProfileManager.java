// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

// todo deprecate
// cannot be interface due to backward compatibility
public abstract class InspectionProjectProfileManager implements InspectionProfileManager {
  public static InspectionProjectProfileManager getInstance(@NotNull Project project) {
    return project.getService(InspectionProjectProfileManager.class);
  }

  /**
   * @deprecated use {@link #getCurrentProfile()}
   */
  @Deprecated(forRemoval = true)
  public @NotNull InspectionProfile getInspectionProfile() {
    return getCurrentProfile();
  }

  public static boolean isInformationLevel(String shortName, @NotNull PsiElement element) {
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key != null) {
      HighlightDisplayLevel errorLevel = getInstance(element.getProject()).getCurrentProfile().getErrorLevel(key, element);
      return HighlightDisplayLevel.DO_NOT_SHOW.equals(errorLevel);
    }
    return false;
  }
}

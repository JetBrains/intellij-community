/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    return project.getComponent(ProjectInspectionProfileManager.class);
  }

  @NotNull
  @Deprecated
  public InspectionProfile getInspectionProfile() {
    return getCurrentProfile();
  }

  /**
   * @deprecated use {@link #getCurrentProfile()} instead
   */
  @Deprecated
  @NotNull
  public InspectionProfile getInspectionProfile(PsiElement element) {
    return getCurrentProfile();
  }

  public static boolean isInformationLevel(String shortName, @NotNull PsiElement element) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key != null) {
      final HighlightDisplayLevel errorLevel = getInstance(element.getProject()).getCurrentProfile().getErrorLevel(key, element);
      return HighlightDisplayLevel.DO_NOT_SHOW.equals(errorLevel);
    }
    return false;
  }
}

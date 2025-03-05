// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface InspectionProfileLoader<T extends InspectionProfileImpl> {
  @Nullable
  T loadProfileByName(@NotNull String profileName);

  @Nullable
  T loadProfileByPath(@NotNull String profilePath);

  default @Nullable T tryLoadProfileByNameOrPath(@Nullable String profileName, @Nullable String profilePath,
                                                 @NotNull String configSource, @NotNull Consumer<@NotNull String> onFailure) {
    //fetch profile by name from project file (project profiles can be disabled)
    if (profileName != null && !profileName.isEmpty()) {
      T inspectionProfile = loadProfileByName(profileName);
      if (inspectionProfile == null) {
        onFailure.accept(InspectionsBundle.message("inspection.application.profile.was.not.found.by.name.0.1", profileName, configSource));
      }
      return inspectionProfile;
    }

    if (profilePath != null && !profilePath.isEmpty()) {
      T inspectionProfile = loadProfileByPath(profilePath);
      if (inspectionProfile == null) {
        onFailure.accept(InspectionsBundle.message("inspection.application.profile.failed.configure.by.path.0.1", profilePath, configSource));
      }
      return inspectionProfile;
    }
    return null;
  }
}

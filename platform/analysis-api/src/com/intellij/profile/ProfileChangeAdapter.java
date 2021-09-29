// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProfileChangeAdapter {
  @Topic.ProjectLevel
  Topic<ProfileChangeAdapter> TOPIC = new Topic<>(ProfileChangeAdapter.class, Topic.BroadcastDirection.NONE);

  default void profileChanged(@NotNull InspectionProfile profile) {
  }

  default void profileActivated(@Nullable InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
  }

  default void profilesInitialized() {
  }
}

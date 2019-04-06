// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;

public interface ProfileChangeAdapter {
  Topic<ProfileChangeAdapter> TOPIC = new Topic<>("ProfileChangeAdapter", ProfileChangeAdapter.class);

  default void profileChanged(@Nullable InspectionProfile profile) {
  }

  default void profileActivated(@Nullable InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
  }

  default void profilesInitialized() {
  }

  default void profilesShutdown() {
  }
}

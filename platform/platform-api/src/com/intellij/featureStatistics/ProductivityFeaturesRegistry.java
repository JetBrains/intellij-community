// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public abstract class ProductivityFeaturesRegistry {
  @NotNull
  public abstract Set<String> getFeatureIds();

  public abstract FeatureDescriptor getFeatureDescriptor(@NotNull String id);

  public abstract @Nullable FeatureDescriptor getFeatureDescriptorByLogEvent(@NotNull String groupId,
                                                                             @NotNull String eventId,
                                                                             @NotNull Map<String, Object> eventData);

  public abstract GroupDescriptor getGroupDescriptor(@NotNull String id);

  public abstract ApplicabilityFilter @NotNull [] getMatchingFilters(@NotNull String featureId);

  public static @Nullable ProductivityFeaturesRegistry getInstance() {
    return ApplicationManager.getApplication().getService(ProductivityFeaturesRegistry.class);
  }
}

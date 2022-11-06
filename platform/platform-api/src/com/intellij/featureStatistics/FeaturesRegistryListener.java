// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface FeaturesRegistryListener {
  void featureUsed(@NotNull FeatureDescriptor feature);

  @Topic.AppLevel
  Topic<FeaturesRegistryListener> TOPIC = new Topic<>("Features Registry listener", FeaturesRegistryListener.class, Topic.BroadcastDirection.NONE);
}

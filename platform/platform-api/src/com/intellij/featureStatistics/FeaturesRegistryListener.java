// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface FeaturesRegistryListener {
  void featureUsed(@NotNull FeatureDescriptor feature);

  @Topic.AppLevel
  Topic<FeaturesRegistryListener> TOPIC = Topic.create("Features Registry listener", FeaturesRegistryListener.class);
}

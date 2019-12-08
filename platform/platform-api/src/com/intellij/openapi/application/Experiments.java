// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Service
public final class Experiments {
  public static final ExtensionPointName<ExperimentalFeature> EP_NAME = ExtensionPointName.create("com.intellij.experimentalFeature");
  private static final Logger LOG = Logger.getInstance(Experiments.class);

  private final Map<String, Boolean> cache = ContainerUtil.newConcurrentMap();

  @NotNull
  public static Experiments getInstance() {
    if (ApplicationManager.getApplication() == null) {
      //usages from UI Designer preview where no application available
      return new Experiments();
    }
    return ServiceManager.getService(Experiments.class);
  }

  public boolean isFeatureEnabled(@NotNull String featureId) {
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      return false;
    }

    Boolean result = cache.get(featureId);
    if (result == null) {
      result = calcIsFeatureEnabled(featureId);
      cache.put(featureId, result);
    }
    return result;
  }

  private static boolean calcIsFeatureEnabled(@NotNull String featureId) {
    ExperimentalFeature feature = getFeatureById(featureId);
    if (feature != null) {
      String key = toPropertyKey(feature);
      if (PropertiesComponent.getInstance().isValueSet(key)) {
        return PropertiesComponent.getInstance().getBoolean(key, false);
      }
      return feature.isEnabled();
    }
    return false;
  }

  public void setFeatureEnabled(@NotNull String featureId, boolean enabled) {
    ExperimentalFeature feature = getFeatureById(featureId);
    if (feature != null) {
      cache.put(featureId, enabled);
      String key = toPropertyKey(feature);
      PropertiesComponent.getInstance().setValue(key, enabled, feature.isEnabled());
      LOG.info("Experimental feature '" + featureId + "' is now turned " + (enabled ? "ON" : "OFF"));
    }
  }

  @Nullable
  private static ExperimentalFeature getFeatureById(@NotNull String featureId) {
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      return null;
    }
    return EP_NAME.findFirstSafe(feature -> feature.id.equals(featureId));
  }

  public boolean isChanged(@NotNull String featureId) {
    ExperimentalFeature feature = getFeatureById(featureId);
    return feature != null && feature.isEnabled() != isFeatureEnabled(featureId);
  }

  private static String toPropertyKey(ExperimentalFeature feature) {
    return "experimentalFeature." + feature.id;
  }
}

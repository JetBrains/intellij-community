// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public final class Experiments {
  public static final ExtensionPointName<ExperimentalFeature> EP_NAME = ExtensionPointName.create("com.intellij.experimentalFeature");
  private static final Logger LOG = Logger.getInstance(Experiments.class);

  private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

  public Experiments() {
    // log enabled experimental features
    NonUrgentExecutor.getInstance().execute(() -> {
      List<String> enabledIds = new SmartList<>();
      PropertiesComponent propertyManager = PropertiesComponent.getInstance();
      for (ExperimentalFeature feature : EP_NAME.getExtensionList()) {
        Boolean result = cache.get(feature.id);
        if (result == null) {
          result = calcIsFeatureEnabled(feature, propertyManager);
          cache.put(feature.id, result);
        }

        if (result) {
          enabledIds.add(feature.id);
        }
      }

      if (!enabledIds.isEmpty()) {
        LOG.info("Experimental features enabled for user: " + StringUtil.join(enabledIds, ", "));
      }
    });
  }

  public static @NotNull Experiments getInstance() {
    return ApplicationManager.getApplication().getService(Experiments.class);
  }

  public boolean isFeatureEnabled(@NonNls @NotNull String featureId) {
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      return false;
    }

    Boolean result = cache.get(featureId);
    if (result == null) {
      ExperimentalFeature feature = getFeatureById(featureId);
      result = feature != null && calcIsFeatureEnabled(feature, PropertiesComponent.getInstance());
      cache.put(featureId, result);
    }
    return result;
  }

  private static boolean calcIsFeatureEnabled(@NotNull ExperimentalFeature feature, @NotNull PropertiesComponent propertyManager) {
    String key = toPropertyKey(feature);
    return propertyManager.isValueSet(key) ? propertyManager.getBoolean(key, false) : feature.isEnabled();
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

  private static @Nullable ExperimentalFeature getFeatureById(@NotNull String featureId) {
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      return null;
    }
    return EP_NAME.findFirstSafe(feature -> feature.id.equals(featureId));
  }

  public boolean isChanged(@NotNull String featureId) {
    ExperimentalFeature feature = getFeatureById(featureId);
    return feature != null && feature.isEnabled() != isFeatureEnabled(featureId);
  }

  private static String toPropertyKey(@NotNull ExperimentalFeature feature) {
    return "experimentalFeature." + feature.id;
  }
}

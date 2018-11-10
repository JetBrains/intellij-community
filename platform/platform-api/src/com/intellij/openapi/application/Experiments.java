// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class Experiments {
  public static final ExtensionPointName<ExperimentalFeature> EP_NAME = ExtensionPointName.create("com.intellij.experimentalFeature");
  private static final Logger LOG = Logger.getInstance(Experiments.class);
  private static final Map<String, Boolean> myCache = ContainerUtil.newConcurrentMap();

  public static boolean isFeatureEnabled(String featureId) {
    if (ApplicationManager.getApplication() == null) {
      return false;
    }
    Boolean result = myCache.get(featureId);
    if (result == null) {
      result = calcIsFeatureEnabled(featureId);
      myCache.put(featureId, result);
    }
    return result;
  }

  private static boolean calcIsFeatureEnabled(String featureId) {
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

  public static void setFeatureEnabled(String featureId, boolean enabled) {
    ExperimentalFeature feature = getFeatureById(featureId);
    if (feature != null) {
      myCache.put(featureId, enabled);
      String key = toPropertyKey(feature);
      PropertiesComponent.getInstance().setValue(key, enabled, feature.isEnabled());
      LOG.info("Experimental feature '" + featureId + "' is now turned " + (enabled ? "ON" : "OFF"));
    }
  }

  @Nullable
  private static ExperimentalFeature getFeatureById(String featureId) {
    for (ExperimentalFeature feature : EP_NAME.getExtensions()) {
      if (feature.id.equals(featureId)) {
        return feature;
      }
    }
    return null;
  }

  public static boolean isChanged(String featureId) {
    ExperimentalFeature feature = getFeatureById(featureId);
    return feature != null && feature.isEnabled() != isFeatureEnabled(featureId);
  }

  private static String toPropertyKey(ExperimentalFeature feature) {
    return "experimentalFeature." + feature.id;
  }
}

// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.application;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class Experiments {
  public static final ExtensionPointName<ExperimentalFeature> EP_NAME = ExtensionPointName.create("com.intellij.experimentalFeature");
  private static final Logger LOG = Logger.getInstance(Experiments.class);

  public static boolean isFeatureEnabled(String featureId) {
    if (ApplicationManager.getApplication() == null) {
      return false;
    }

    for (ExperimentalFeature feature : EP_NAME.getExtensions()) {
      if (feature.id.equals(featureId)) {
        String key = toPropertyKey(feature);
        if (PropertiesComponent.getInstance().isValueSet(key)) {
          return PropertiesComponent.getInstance().getBoolean(key, false);
        }
        return feature.isEnabled();
      }
    }

    return false;
  }

  public static void setFeatureEnabled(String featureId, boolean enabled) {
    ExperimentalFeature feature = getFeatureById(featureId);
    if (feature != null) {
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

  private static String toPropertyKey(ExperimentalFeature feature) {
    return "experimentalFeature." + feature.id;
  }
}

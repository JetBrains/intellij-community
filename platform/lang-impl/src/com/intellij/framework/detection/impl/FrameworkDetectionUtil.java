/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.framework.detection.impl;

import com.intellij.facet.FacetType;
import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PlatformModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FrameworkDetectionUtil {
  private FrameworkDetectionUtil() {
  }

  @Nullable
  public static FrameworkType findFrameworkTypeForFacetDetector(@NotNull FacetType<?, ?> facetType) {
    for (FrameworkDetector detector : FrameworkDetector.EP_NAME.getExtensions()) {
      if (detector instanceof FacetBasedFrameworkDetector<?, ?> &&
          ((FacetBasedFrameworkDetector)detector).getFacetType().equals(facetType)) {
        return detector.getFrameworkType();
      }
    }
    return null;
  }

  public static List<DetectedFrameworkDescription> getDisabledDescriptions(@NotNull List<? extends DetectedFrameworkDescription> allDescriptions) {
    List<DetectedFrameworkDescription> disabled = new ArrayList<DetectedFrameworkDescription>();
    for (DetectedFrameworkDescription description : allDescriptions) {
      if (!description.canSetupFramework(allDescriptions)) {
        disabled.add(description);
      }
    }
    if (!disabled.isEmpty()) {
      List<DetectedFrameworkDescription> remaining = new ArrayList<DetectedFrameworkDescription>(allDescriptions);
      remaining.removeAll(disabled);
      disabled.addAll(getDisabledDescriptions(remaining));
    }
    return disabled;
  }

  public static List<? extends DetectedFrameworkDescription> removeDisabled(@NotNull List<? extends DetectedFrameworkDescription> allDescriptions) {
    final List<DetectedFrameworkDescription> disabled = getDisabledDescriptions(allDescriptions);
    if (disabled.isEmpty()) return allDescriptions;
    final List<DetectedFrameworkDescription> descriptions = new ArrayList<DetectedFrameworkDescription>(allDescriptions);
    descriptions.removeAll(disabled);
    return descriptions;
  }

  public static void setupFrameworks(List<? extends DetectedFrameworkDescription> descriptions, final Project project) {
    AccessToken token = WriteAction.start();
    try {
      final PlatformModifiableModelsProvider provider = new PlatformModifiableModelsProvider();
      final DefaultModulesProvider modulesProvider = new DefaultModulesProvider(project);
      List<DetectedFrameworkDescription> sortedDescriptions = new ArrayList<DetectedFrameworkDescription>();
      for (DetectedFrameworkDescription description : descriptions) {
        if (description.getUnderlyingType() == null) {
          sortedDescriptions.add(description);
        }
      }
      for (DetectedFrameworkDescription description : descriptions) {
        if (description.getUnderlyingType() != null) {
          sortedDescriptions.add(description);
        }
      }
      for (DetectedFrameworkDescription description : sortedDescriptions) {
        description.setupFramework(provider, modulesProvider);
      }
    }
    finally {
      token.finish();
    }
  }
}

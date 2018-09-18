// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.configurations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class ConfigurationTypeUtil {
  private ConfigurationTypeUtil() {
  }

  @NotNull
  public static <T extends ConfigurationType> T findConfigurationType(@NotNull Class<T> configurationTypeClass) {
    List<ConfigurationType> types = ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList();
    for (ConfigurationType type : types) {
      if (configurationTypeClass.isInstance(type)) {
        //noinspection unchecked
        return (T)type;
      }
    }
    throw new AssertionError(types + " loader: " + configurationTypeClass.getClassLoader() +
                             ", " + configurationTypeClass);
  }

  public static boolean equals(@NotNull ConfigurationType type1, @NotNull ConfigurationType type2) {
    return type1.getId().equals(type2.getId());
  }

  @Nullable
  public static ConfigurationType findConfigurationType(String configurationId) {
    List<ConfigurationType> types = ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList();
    for (ConfigurationType type : types) {
      if (type.getId().equals(configurationId)) {
        return type;
      }
    }
    return null;
  }
}

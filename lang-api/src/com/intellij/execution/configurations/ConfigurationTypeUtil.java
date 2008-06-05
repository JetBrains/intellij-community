package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.Extensions;

/**
 * @author yole
 */
public class ConfigurationTypeUtil {
  private ConfigurationTypeUtil() {
  }

  public static <T extends ConfigurationType> T findConfigurationType(final Class<T> configurationTypeClass) {
    ConfigurationType[] types = Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP);
    for (ConfigurationType type : types) {
      if (configurationTypeClass.isInstance(type)) {
        //noinspection unchecked
        return (T)type;
      }
    }
    assert false;
    return null;
  }
}

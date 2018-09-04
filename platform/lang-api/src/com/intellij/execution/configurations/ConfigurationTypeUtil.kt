// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.util.containers.ContainerUtil

object ConfigurationTypeUtil {
  @JvmStatic
  fun <T : ConfigurationType> findConfigurationType(configurationTypeClass: Class<T>): T {
    val types = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
    for (type in types) {
      if (configurationTypeClass.isInstance(type)) {
        @Suppress("UNCHECKED_CAST")
        return type as T
      }
    }

    throw AssertionError("$types loader: ${configurationTypeClass.classLoader}, ${configurationTypeClass}")
  }

  @JvmStatic
  fun equals(type1: ConfigurationType, type2: ConfigurationType): Boolean {
    return type1.id == type2.id
  }

  @JvmStatic
  fun findConfigurationType(configurationId: String): ConfigurationType? {
    return ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.firstOrNull { it.id == configurationId }
  }

  @JvmStatic
  fun getTypesWithUnknown(): List<ConfigurationType> {
    return ContainerUtil.concat(ConfigurationType.CONFIGURATION_TYPE_EP.extensionList, listOf(UnknownConfigurationType.INSTANCE))
  }
}

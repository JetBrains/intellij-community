// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.l10n

import java.nio.file.Path

enum class LocalizationOrder {
  FOLDER_REGION_LEVEL_PLUGIN,  //localization/zh/CN/
  FOLDER_REGION_LEVEL_PLATFORM,
  SUFFIX_REGION_LEVEL_PLUGIN,  //name_zh_CN.properties
  SUFFIX_REGION_LEVEL_PLATFORM,
  FOLDER_LANGUAGE_LEVEL_PLUGIN,  //localization/zh/
  FOLDER_LANGUAGE_LEVEL_PLATFORM,
  SUFFIX_LANGUAGE_LEVEL_PLUGIN,  //name_zh.properties
  SUFFIX_LANGUAGE_LEVEL_PLATFORM,
  DEFAULT_PLUGIN,  //name.properties
  DEFAULT_PLATFORM;

  companion object {
    fun getLocalizationOrder(orderedPaths: List<Path?>, bundlePath: Path, isPluginClassLoader: Boolean): LocalizationOrder? {
      var order = orderedPaths.indexOf(bundlePath)
      order = if (isPluginClassLoader) order * 2 else order * 2 + 1
      return if (0 <= order && order < entries.size) entries[order] else null
    }
  }
}
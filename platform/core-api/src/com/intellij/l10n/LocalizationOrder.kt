// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class LocalizationOrder {
  FOLDER_REGION_LEVEL_PLATFORM, //localization/zh/CN/
  SUFFIX_REGION_LEVEL_PLATFORM, //name_zh_CN.properties
  FOLDER_LANGUAGE_LEVEL_PLATFORM, //localization/zh/
  SUFFIX_LANGUAGE_LEVEL_PLATFORM,  //name_zh.properties
  DEFAULT_PLUGIN,  //name.properties inside plugin
  DEFAULT_PLATFORM; //name.properties

  companion object {
    fun getLocalizationOrder(orderedPaths: List<String>, bundlePath: String): LocalizationOrder? {
      return when (orderedPaths.size) {
        1 ->  DEFAULT_PLATFORM
        3 -> {
          when (orderedPaths.indexOf(bundlePath)) {
            0 ->  FOLDER_LANGUAGE_LEVEL_PLATFORM
            1 ->  SUFFIX_LANGUAGE_LEVEL_PLATFORM
            else -> DEFAULT_PLATFORM
          }
        }
        else -> {
          val order = orderedPaths.indexOf(bundlePath)
          if (order == DEFAULT_PLUGIN.ordinal) DEFAULT_PLATFORM
          else if (order >= 0 && order < entries.size) entries[order] else null
        }
      }
    }
  }
}
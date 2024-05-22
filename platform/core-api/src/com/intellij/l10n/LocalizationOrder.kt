// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
enum class LocalizationOrder {
  DEFAULT_PLUGIN,
  FOLDER_REGION_LEVEL_PLATFORM, //localization/zh/CN/
  SUFFIX_REGION_LEVEL_PLATFORM, //name_zh_CN.properties
  FOLDER_LANGUAGE_LEVEL_PLATFORM, //localization/zh/
  SUFFIX_LANGUAGE_LEVEL_PLATFORM,  //name_zh.properties
  DEFAULT_PLATFORM; //name.properties

  companion object {
    fun getLocalizationOrder(orderedPaths: List<Path?>, bundlePath: Path): LocalizationOrder? {
      val order = orderedPaths.indexOf(bundlePath) + 1
      return if (order >= 0 && order < entries.size) entries[order] else null
    }
  }
}
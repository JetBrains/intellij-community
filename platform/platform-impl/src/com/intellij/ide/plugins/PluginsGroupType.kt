// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PluginsGroupType {
  BUNDLED_UPDATE,
  UPDATE,
  INSTALLING,
  INSTALLED,
  SEARCH_INSTALLED,
  SEARCH,
  STAFF_PICKS,
  NEW_AND_UPDATED,
  TOP_DOWNLOADS,
  TOP_RATED,
  CUSTOM_REPOSITORY,
  INTERNAL,
  SUGGESTED;
}
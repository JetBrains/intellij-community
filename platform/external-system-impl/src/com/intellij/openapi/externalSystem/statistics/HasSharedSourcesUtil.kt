// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

/**
 * utility for persisting information about found duplicating source roots (aka shared source roots)
 * this info is necessary for [ExternalSystemSettingsCollector] in order to report it to FUS
 */
object HasSharedSourcesUtil {
  @JvmStatic
  fun hasSharedSources(project: Project): Boolean =
    project.storage().getBoolean(HAS_SHARED_SOURCES_KEY)

  @JvmStatic
  fun setHasSharedSources(project: Project, hasDuplicates: Boolean) =
    project.storage().setValue(HAS_SHARED_SOURCES_KEY, hasDuplicates)

  private fun Project.storage(): PropertiesComponent =
    PropertiesComponent.getInstance(this)

  private const val HAS_SHARED_SOURCES_KEY = "external.system.has.shared.sources"
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Historically IDE used scope presentable name for serialization (like "Project Files").
 * This approach doesn't work anymore because scope presentable names are different in different locales (different translation bundles).
 * This class helps to map scope serialization IDs to presentable scope name and vice versa.
 * For compatibility, scope serialization IDs are hardcoded in this class, and they are equal to scope presentable names in English locale.
 */

@ApiStatus.Internal
abstract class ScopeIdMapper {
  companion object {
    @JvmStatic
    val instance: ScopeIdMapper
      get() = ApplicationManager.getApplication().getService(ScopeIdMapper::class.java)

    // Scope serialization ids are equal to English scope translations (for compatibility with 2020.2-)
    @NonNls
    const val ALL_PLACES_SCOPE_ID = "All Places"

    @NonNls
    const val PROJECT_AND_LIBRARIES_SCOPE_ID = "Project and Libraries"

    @NonNls
    const val PROJECT_FILES_SCOPE_ID = "Project Files"

    @NonNls
    const val PROJECT_PRODUCTION_FILES_SCOPE_ID = "Project Production Files"

    @NonNls
    const val PROJECT_TEST_FILES_SCOPE_ID = "Project Test Files"

    @NonNls
    const val SCRATCHES_AND_CONSOLES_SCOPE_ID = "Scratches and Consoles"

    @NonNls
    const val RECENTLY_VIEWED_FILES_SCOPE_ID = "Recently Viewed Files"

    @NonNls
    const val RECENTLY_CHANGED_FILES_SCOPE_ID = "Recently Changed Files"

    @NonNls
    const val OPEN_FILES_SCOPE_ID = "Open Files"

    @NonNls
    const val CURRENT_FILE_SCOPE_ID = "Current File"

    @JvmStatic
    val standardNames = setOf(ALL_PLACES_SCOPE_ID, PROJECT_AND_LIBRARIES_SCOPE_ID, PROJECT_FILES_SCOPE_ID, PROJECT_PRODUCTION_FILES_SCOPE_ID,
                              PROJECT_TEST_FILES_SCOPE_ID, SCRATCHES_AND_CONSOLES_SCOPE_ID, RECENTLY_VIEWED_FILES_SCOPE_ID, RECENTLY_CHANGED_FILES_SCOPE_ID,
                              RECENTLY_CHANGED_FILES_SCOPE_ID, OPEN_FILES_SCOPE_ID, CURRENT_FILE_SCOPE_ID)
  }

  @Nls
  abstract fun getPresentableScopeName(@NonNls scopeId: String): String

  @NonNls
  abstract fun getScopeSerializationId(@Nls presentableScopeName: String): String
}
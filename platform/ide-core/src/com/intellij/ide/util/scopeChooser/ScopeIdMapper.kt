// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Historically, IDE used a scope's presentable name for serialization (like "Project Files").
 * This approach doesn't work anymore because presentable names are different in different locales (different translation bundles).
 * This class helps to map scope serialization IDs to presentable scope name and vice versa.
 * For compatibility, scope serialization IDs are hardcoded in this class, and they are equal to presentable names in the English locale.
 */
@ApiStatus.Internal
abstract class ScopeIdMapper {
  companion object {
    @JvmStatic
    val instance: ScopeIdMapper
      get() = ApplicationManager.getApplication().getService(ScopeIdMapper::class.java)

    // scope serialization IDs are equal to English scope translations (for compatibility with 2020.2-)
    const val ALL_PLACES_SCOPE_ID: String = "All Places"
    const val PROJECT_AND_LIBRARIES_SCOPE_ID: String = "Project and Libraries"
    const val PROJECT_FILES_SCOPE_ID: String = "Project Files"
    const val PROJECT_PRODUCTION_FILES_SCOPE_ID: String = "Project Production Files"
    const val PROJECT_TEST_FILES_SCOPE_ID: String = "Project Test Files"
    const val SCRATCHES_AND_CONSOLES_SCOPE_ID: String = "Scratches and Consoles"
    const val RECENTLY_VIEWED_FILES_SCOPE_ID: String = "Recently Viewed Files"
    const val RECENTLY_CHANGED_FILES_SCOPE_ID: String = "Recently Changed Files"
    const val OPEN_FILES_SCOPE_ID: String = "Open Files"
    const val CURRENT_FILE_SCOPE_ID: String = "Current File"

    /**
     * **Note**: the list is used in some FUS collectors; remember bumping the versions on adding/removing/renaming values.
     */
    @JvmStatic
    val standardNames: Set<String> = setOf(
      ALL_PLACES_SCOPE_ID, PROJECT_AND_LIBRARIES_SCOPE_ID, PROJECT_FILES_SCOPE_ID, PROJECT_PRODUCTION_FILES_SCOPE_ID,
      PROJECT_TEST_FILES_SCOPE_ID, SCRATCHES_AND_CONSOLES_SCOPE_ID, RECENTLY_VIEWED_FILES_SCOPE_ID, RECENTLY_CHANGED_FILES_SCOPE_ID,
      RECENTLY_CHANGED_FILES_SCOPE_ID, OPEN_FILES_SCOPE_ID, CURRENT_FILE_SCOPE_ID
    )
  }

  abstract fun getPresentableScopeName(scopeId: String): @Nls String

  abstract fun getScopeSerializationId(presentableScopeName: @Nls String): String
}

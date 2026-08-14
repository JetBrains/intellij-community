// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent

@JvmDefaultWithCompatibility
interface FindPopupScopeUI {
  companion object {
    @ApiStatus.Internal
    const val PROJECT_SCOPE_NAME: String = "Project"

    @ApiStatus.Internal
    const val MODULE_SCOPE_NAME: String = "Module"

    @ApiStatus.Internal
    const val DIRECTORY_SCOPE_NAME: String = "Directory"

    @ApiStatus.Internal
    const val CUSTOM_SCOPE_SCOPE_NAME: String = "Scope"
  }

  fun getComponents(): Array<Pair<ScopeType, JComponent>>

  fun initByModel(findModel: FindModel): ScopeType
  fun applyTo(findSettings: FindSettings, selectedScope: ScopeType)
  fun applyTo(findModel: FindModel, selectedScope: ScopeType)

  @ApiStatus.Internal
  fun isDirectoryScope(scopeType: ScopeType?): Boolean {
    return false
  }

  @ApiStatus.Internal
  fun getScopeTypeByModel(findModel: FindModel): ScopeType? {
    return null
  }

  /**
   * @return null means OK
   */
  fun validate(model: FindModel, selectedScope: ScopeType?): ValidationInfo? {
    return null
  }

  /**
   * @return true if something was hidden
   */
  fun hideAllPopups(): Boolean

  @ApiStatus.Internal
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  fun evaluateValidationInfo(isDirectoryExists: java.lang.Boolean): ValidationInfo? {
    return null
  }

  /**
   * Cancels the activities of the current Find popup session.
   */
  fun cancelActivities() {
  }

  /**
   * Suspends until the scope selection started by the last user action has been applied on the backend.
   */
  suspend fun awaitScopeSelection() {
  }

  class ScopeType(
    @JvmField val name: String,
    @JvmField var textComputable: Supplier<@NlsContexts.ListItem String>,
    @JvmField val icon: Icon,
  )
}

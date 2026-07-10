// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Controls whether a non-modal settings dialog is available
 *
 * Products that don't support non-modal settings (e.g. Rider, CLion Nova)
 * can register an extension to disable the feature and hide the corresponding Advanced Setting.
 *
 * This policy is intentionally separated from [ShowSettingsUtil] to avoid
 * service-override ordering issues when multiple plugins override [ShowSettingsUtil]
 * (e.g. CWM's `BackendShowSettingsUtil` and Rider's `RiderShowSettingsUtilImpl`).
 */
@ApiStatus.Internal
interface NonModalSettingsPolicy {
  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<NonModalSettingsPolicy> = ExtensionPointName.create("com.intellij.nonModalSettingsPolicy")

    /**
     * Aggregated check for all registered [NonModalSettingsPolicy] extensions.
     */
    fun isNonModalSettingsEnabledByAllPolicies(): Boolean {
      if (EP_NAME.extensionList.any { !it.isNonModalSettingsEnabled() }) {
        return false
      }
      return System.getProperty("ide.ui.non.modal.settings.window")?.toBoolean()
             ?: com.intellij.openapi.options.advanced.AdvancedSettings.getBoolean("ide.ui.non.modal.settings.window")
    }

    /**
     * Aggregated check for all registered [NonModalSettingsPolicy] extensions.
     */
    fun isNonModalSettingsWindowSettingVisibleInAllPolicies(): Boolean {
      return EP_NAME.extensionList.all { it.isNonModalSettingsWindowSettingVisible() }
    }
  }

  /**
   * Whether the non-modal settings window feature is enabled for this product.
   * When `false`, settings are always shown in a modal dialog.
   */
  fun isNonModalSettingsEnabled(): Boolean

  /**
   * Controls the visibility of the "Show Settings in non-modal window" option in Advanced Settings.
   * When `false`, the Advanced Setting is hidden (along with non-modal settings being disabled).
   */
  fun isNonModalSettingsWindowSettingVisible(): Boolean
}


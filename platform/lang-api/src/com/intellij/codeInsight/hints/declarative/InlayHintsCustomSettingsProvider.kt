// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * Provider of a custom settings page for the declarative inlay hints.
 *
 * Responsible for UI component creation.
 *
 * Doesn't apply changes immediately, only in [persistSettings]
 */
interface InlayHintsCustomSettingsProvider<T> {
  companion object {
    fun getCustomSettingsProvider(providerId: String, language: Language): InlayHintsCustomSettingsProvider<*>? {
      for (extensionPoint in InlayHintsCustomSettingsProviderBean.EP.extensionList) {
        val extensionLanguage = extensionPoint.language
        if (extensionPoint.providerId == providerId && extensionLanguage != null && language.isKindOf(extensionLanguage)) {
          return extensionPoint.instance
        }
      }
      return null
    }
  }

  /**
   * Create a settings component for the user to change the settings.
   */
  fun createComponent(project: Project, language: Language) : JComponent

  /**
   * To check if settings have changed.
   */
  fun isDifferentFrom(project: Project, settings: T) : Boolean

  /**
   * @return a copy of the settings used to check if the settings have changed.
   */
  fun getSettingsCopy() : T

  /**
   * Unused method
   */
  fun putSettings(project: Project, settings: T, language: Language)

  /**
   * Store the specifiedsettings, e.g. after the Apply or OK button has been clicked.
   */
  fun persistSettings(project: Project, settings: T, language: Language)
}
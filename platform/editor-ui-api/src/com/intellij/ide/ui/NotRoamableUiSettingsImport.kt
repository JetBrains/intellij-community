// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMigration
import com.intellij.platform.settings.v2.SettingsMutationBuilder
import com.intellij.platform.settings.v2.mutate
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Imports the state of the [NotRoamableUiSettings] component into the keys of [UISettingsKeys]. The migration writes a
 * value only into a key that holds no stored value, and only when the value differs from the default. So it stores no
 * default, and a repeated run keeps the values that the user set after the first import. The component repairs its
 * state when it loads it, so the import takes the repaired values.
 * Registered as the `settings.v2.appMigration` `ui.importNotRoamableUiSettings` in `intellij.platform.editor.ui.xml`.
 */
@Internal
class NotRoamableUiSettingsImport : SettingsMigration {
  override suspend fun migrate(context: SettingsContext) {
    val state = serviceAsync<NotRoamableUiSettings>().state
    context.mutate().importValues(context, state).apply()
  }
}

/**
 * Adds the import of each value of [state] that has a key. See [importIfUnset]. `fontScale` and
 * `overrideLafFontsWasMigrated` have no key, so they stay in the state.
 *
 * A font size of `0` or less exists only before the component loads a stored state, because the load repairs it. A
 * presentation mode scale of `0` or less exists only before [UISettings] loads its state. So the import skips both.
 */
private fun SettingsMutationBuilder.importValues(context: SettingsContext, state: NotRoamableUiOptions): SettingsMutationBuilder {
  return importIfUnset(context, UISettingsKeys.IDE_ANTIALIASING, state.ideAAType)
    .importIfUnset(context, UISettingsKeys.EDITOR_ANTIALIASING, state.editorAAType)
    .importIfUnset(context, UISettingsKeys.CUSTOM_FONT_FACE, state.fontFace)
    .let { builder ->
      if (state.fontSize <= 0) builder
      else builder.importIfUnset(context, UISettingsKeys.CUSTOM_FONT_SIZE, state.fontSize)
    }
    .importIfUnset(context, UISettingsKeys.CUSTOM_FONT_ENABLED, state.overrideLafFonts)
    .importIfUnset(context, UISettingsKeys.IDE_SCALE, state.ideScale)
    .let { builder ->
      if (state.presentationModeIdeScale <= 0) builder
      else builder.importIfUnset(context, UISettingsKeys.PRESENTATION_MODE_IDE_SCALE, state.presentationModeIdeScale)
    }
    .importIfUnset(context, UISettingsKeys.SINGLE_STRIPE, state.experimentalSingleStripe)
}

/**
 * Adds a write of the old [value] for [key] when [context] holds no stored value of [key], and [value] differs from
 * the value that [key] resolves to, which is then its default.
 */
private fun <T> SettingsMutationBuilder.importIfUnset(context: SettingsContext, key: SettingsKey<T>, value: T): SettingsMutationBuilder =
  if (key.values(context).isNotEmpty() || key[context] == value) this else set(key, value)

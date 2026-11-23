// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMigration
import com.intellij.platform.settings.v2.SettingsMutationBuilder

/**
 * Imports the state of the [PresentationAssistant] component, which `presentation-assistant-ij.xml` holds, into the
 * keys of [PresentationAssistantSettings]. Nothing else reads that state. The migration writes a value only into a key
 * that holds no stored value, and only when the value differs from the default. So it stores no default, and a
 * repeated run keeps the values that the user set after the first import. The old file does not change after the
 * import, so a repeated run would otherwise put the old values back. The presenter takes the imported values at once.
 * Registered as the `settings.v2.appMigration` `presentationAssistant.importComponentState` in
 * `intellij.platform.ide.presentationAssistant.xml`.
 */
internal class PresentationAssistantSettingsImport : SettingsMigration {
  override suspend fun migrate(context: SettingsContext) {
    val presentationAssistant = serviceAsync<PresentationAssistant>()
    PresentationAssistantSettings.startChange().importValues(presentationAssistant.state).apply()
  }
}

/**
 * Adds the import of each value of [state] that has a key. See [importIfUnset]. The offsets of a drag have no key, so
 * they stay in the state.
 *
 * @return this builder
 */
private fun SettingsMutationBuilder.importValues(state: PresentationAssistantState): SettingsMutationBuilder =
  importIfUnset(PresentationAssistantSettings.SHOW_ACTION_DESCRIPTIONS, state.showActionDescriptions)
    .importIfUnset(PresentationAssistantSettings.POPUP_SIZE, PresentationAssistantPopupSize.from(state.popupSize))
    .importIfUnset(PresentationAssistantSettings.POPUP_DURATION_MILLIS, state.popupDuration)
    .importIfUnset(PresentationAssistantSettings.POPUP_ALIGNMENT,
                   PresentationAssistantPopupAlignment.from(state.horizontalAlignment, state.verticalAlignment))
    .importIfUnset(PresentationAssistantSettings.THEME, PresentationAssistantTheme.entries.firstOrNull { it.value == state.theme })
    .importIfUnset(PresentationAssistantSettings.MAIN_KEYMAP_NAME, state.mainKeymapName)
    .importIfUnset(PresentationAssistantSettings.MAIN_KEYMAP_LABEL, state.mainKeymapLabel)
    .importIfUnset(PresentationAssistantSettings.SHOW_ALTERNATIVE_KEYMAP, state.showAlternativeKeymap)
    .importIfUnset(PresentationAssistantSettings.ALTERNATIVE_KEYMAP_NAME, state.alternativeKeymapName)
    .importIfUnset(PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL, state.alternativeKeymapLabel)

/**
 * Adds a write of the old [value] for [key] when the application context holds no stored value of [key], and [value]
 * differs from the default.
 *
 * @return this builder
 */
private fun <T> SettingsMutationBuilder.importIfUnset(key: SettingsKey<T>, value: T): SettingsMutationBuilder =
  if (key.values(SettingsContext.app()).isEmpty()) setIfChanged(key, value) else this

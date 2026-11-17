// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.components.service
import com.intellij.platform.settings.v2.OS
import com.intellij.platform.settings.v2.PER_OS_SETTINGS
import com.intellij.platform.settings.v2.Serializer
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextFilters
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsKeyPromoter
import com.intellij.platform.settings.v2.SettingsMapKey
import com.intellij.platform.settings.v2.SettingsMutationBuilder
import com.intellij.platform.settings.v2.StringSerializer
import com.intellij.platform.settings.v2.access
import com.intellij.platform.settings.v2.enumSerializer
import com.intellij.platform.settings.v2.getValue
import com.intellij.platform.settings.v2.mutate
import com.intellij.platform.settings.v2.nullable

/**
 * Holds the settings keys of the Presentation Assistant. Each key resolves only at the application context, and each
 * key is shareable. The offsets of a dragged popup are not keys but stored state of [PresentationAssistant] with no roaming,
 * so they stay on one machine. The names and the labels of the keymaps live in [PER_OS_SETTINGS], one value for
 * each OS.
 */
internal object PresentationAssistantSettings {
  /** Tells whether the popup shows the actions that the user calls. */
  val SHOW_ACTION_DESCRIPTIONS: SettingsKey<Boolean> = presentationAssistantKey("showActionDescriptions", default = false)

  /** Holds the size of the popup. */
  val POPUP_SIZE: SettingsKey<PresentationAssistantPopupSize> =
    presentationAssistantKey("popupSize", default = PresentationAssistantPopupSize.MEDIUM)

  /** Holds the time in milliseconds that the popup stays visible. */
  val POPUP_DURATION_MILLIS: SettingsKey<Int> = presentationAssistantKey("popupDurationMillis", default = 4 * 1000)

  /** Holds the position of the popup in the IDE frame. The offsets of a drag move the popup from there. */
  val POPUP_ALIGNMENT: SettingsKey<PresentationAssistantPopupAlignment> =
    presentationAssistantKey("popupAlignment", default = PresentationAssistantPopupAlignment.defaultAlignment)

  /** Holds the theme of the popup, or `null` to follow the look and feel. [popupTheme] gives the theme to paint. */
  val THEME: SettingsKey<PresentationAssistantTheme?> =
    presentationAssistantKey("theme", default = null, serializer = enumSerializer<PresentationAssistantTheme>().nullable())

  /**
   * Holds the name of the keymap whose shortcuts the popup shows first. A keymap belongs to one OS, so the key lives
   * in [PER_OS_SETTINGS], as the other keymap keys do.
   */
  val MAIN_KEYMAP_NAME: SettingsKey<String> =
    presentationAssistantKey("mainKeymapName", StringSerializer, storageKey = PER_OS_SETTINGS) { KeymapKind.defaultForOS().value }

  /** Holds the label of the shortcuts of [MAIN_KEYMAP_NAME], per OS. */
  val MAIN_KEYMAP_LABEL: SettingsKey<String> =
    presentationAssistantKey("mainKeymapLabel", StringSerializer, storageKey = PER_OS_SETTINGS) { KeymapKind.defaultForOS().defaultLabel }

  /** Tells whether the popup also shows the shortcuts of [ALTERNATIVE_KEYMAP_NAME]. */
  val SHOW_ALTERNATIVE_KEYMAP: SettingsKey<Boolean> = presentationAssistantKey("showAlternativeKeymap", default = false)

  /** Holds the name of the keymap whose shortcuts the popup shows second, per OS. */
  val ALTERNATIVE_KEYMAP_NAME: SettingsKey<String> =
    presentationAssistantKey("alternativeKeymapName", StringSerializer, storageKey = PER_OS_SETTINGS) {
      KeymapKind.defaultForOS().getAlternativeKind().value
    }

  /** Holds the label of the shortcuts of [ALTERNATIVE_KEYMAP_NAME], per OS. */
  val ALTERNATIVE_KEYMAP_LABEL: SettingsKey<String> =
    presentationAssistantKey("alternativeKeymapLabel", StringSerializer, storageKey = PER_OS_SETTINGS) {
      KeymapKind.defaultForOS().getAlternativeKind().defaultLabel
    }

  /** Lists every key of this object. [PresentationAssistantSettingsPromoter] registers each one. */
  val ALL_KEYS: List<SettingsKey<*>> = listOf(
    SHOW_ACTION_DESCRIPTIONS,
    POPUP_SIZE,
    POPUP_DURATION_MILLIS,
    POPUP_ALIGNMENT,
    THEME,
    MAIN_KEYMAP_NAME,
    MAIN_KEYMAP_LABEL,
    SHOW_ALTERNATIVE_KEYMAP,
    ALTERNATIVE_KEYMAP_NAME,
    ALTERNATIVE_KEYMAP_LABEL,
  )

  /**
   * Holds the alignment of the popup, or `null` when a drag moved the popup away from it. The offsets of a drag are
   * stored state of the [PresentationAssistant] component, not keys.
   */
  val popupAlignmentIfNoDelta: PresentationAssistantPopupAlignment?
    get() {
      val state = service<PresentationAssistant>().configuration
      return POPUP_ALIGNMENT.getValue().takeIf { state.deltaX == null || state.deltaY == null }
    }

  /** Holds the theme to paint the popup with: the [THEME] when themes are enabled, else the theme of the look and feel. */
  val popupTheme: PresentationAssistantTheme
    get() = THEME.getValue()?.takeIf { PresentationAssistant.isThemeEnabled } ?: PresentationAssistantTheme.DEFAULT

  /** Returns the kind of the [MAIN_KEYMAP_NAME] keymap. */
  fun mainKeymapKind(): KeymapKind = KeymapKind.from(MAIN_KEYMAP_NAME.getValue())

  /** Returns the kind of the [ALTERNATIVE_KEYMAP_NAME] keymap, or `null` when the popup does not show it. */
  fun alternativeKeymapKind(): KeymapKind? =
    if (SHOW_ALTERNATIVE_KEYMAP.getValue()) KeymapKind.from(ALTERNATIVE_KEYMAP_NAME.getValue()) else null

  /**
   * Starts a batch of writes at the application context. The caller applies it, for example in [updateAsync]. The
   * context of the batch pins the slot of the current OS, so a write of a key of [PER_OS_SETTINGS] lands there.
   */
  fun startChange(): SettingsMutationBuilder = SettingsContext.app().access(OS).mutate()
}

/**
 * Adds a write of [value] for [key], unless [key] already resolves to [value]. So a write never stores a value that
 * the key already has, such as its default.
 *
 * @return this builder
 */
internal fun <T> SettingsMutationBuilder.setIfChanged(key: SettingsKey<T>, value: T): SettingsMutationBuilder =
  if (key.getValue() == value) this else set(key, value)

/** Starts the name of each key of the Presentation Assistant. */
private const val KEY_NAME_PREFIX: String = "presentationAssistant."

/**
 * Creates a key of the Presentation Assistant with the name `presentationAssistant.<name>` and a default factory.
 * Use it only when the key needs a factory or a storage, as a per-OS key does. Any other key takes a ready default.
 *
 * @param storageKey the map key whose slots hold the value, or `null` for a plain key
 * @param default returns the default value. A factory, because some defaults need the running IDE, such as the
 *   localized label of a keymap.
 */
private fun <T> presentationAssistantKey(
  name: String,
  serializer: Serializer<T>,
  storageKey: SettingsMapKey? = null,
  default: (SettingsContext) -> T,
): SettingsKey<T> =
  SettingsKey(name = KEY_NAME_PREFIX + name,
              serializer = serializer,
              storageKey = storageKey,
              contextFilter = SettingsContextFilters.APP_ONLY,
              defaultFactory = default)

/**
 * Creates a key of the Presentation Assistant with the name `presentationAssistant.<name>` and a ready default value.
 *
 * @param serializer the serializer of the value, or `null` to infer it from [default]. A `null` default needs one.
 */
private fun <T> presentationAssistantKey(name: String, default: T, serializer: Serializer<T>? = null): SettingsKey<T> =
  SettingsKey(name = KEY_NAME_PREFIX + name,
              default = default,
              serializer = serializer,
              contextFilter = SettingsContextFilters.APP_ONLY)

/**
 * Registers the keys of [PresentationAssistantSettings], so a write of them is not a missing promotion.
 * Registered as a `settings.v2.keyPromoter` in `intellij.platform.ide.presentationAssistant.xml`.
 */
internal class PresentationAssistantSettingsPromoter : SettingsKeyPromoter {
  override fun promote(register: (SettingsKey<*>) -> Unit) {
    PresentationAssistantSettings.ALL_KEYS.forEach(register)
  }
}

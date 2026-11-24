// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.settings.v2.DoubleSerializer
import com.intellij.platform.settings.v2.KeyLocality
import com.intellij.platform.settings.v2.Serializer
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextFilters
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsKeyPromoter
import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.StringSerializer
import com.intellij.platform.settings.v2.enumSerializer
import com.intellij.platform.settings.v2.getValue
import com.intellij.platform.settings.v2.nullable
import com.intellij.platform.settings.v2.setValue
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Holds the settings keys of the UI settings that [NotRoamableUiSettings] stores. Each key resolves only at the
 * application context. The fonts, the antialiasing and the scales depend on the display and the fonts of one machine,
 * so their keys are environmental. Only [SINGLE_STRIPE] is shareable.
 */
@Internal
object UISettingsKeys {
  /** Holds the antialiasing of the text in the IDE. The default is subpixel when the machine supports it. */
  val IDE_ANTIALIASING: SettingsKey<AntialiasingType> = antialiasingKey("ui.ideAntialiasing") {
    if (AntialiasingType.canUseSubpixelAAForIDE()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE
  }

  /** Holds the antialiasing of the text in the editor. The default is subpixel when the machine supports it. */
  val EDITOR_ANTIALIASING: SettingsKey<AntialiasingType> = antialiasingKey("ui.editorAntialiasing") {
    if (AntialiasingType.canUseSubpixelAAForEditor()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE
  }

  /** Holds the name of the custom font of the IDE, or `null` for the system font. */
  val CUSTOM_FONT_FACE: SettingsKey<String?> = uiKey("ui.customFont.face", default = null, serializer = StringSerializer.nullable())

  /** Holds the size of the custom font of the IDE, or `0` for the system size. */
  val CUSTOM_FONT_SIZE: SettingsKey<Float> = uiKey("ui.customFont.size", default = 0f, serializer = FloatSerializer)

  /** Tells whether the IDE uses the custom font of [CUSTOM_FONT_FACE] and [CUSTOM_FONT_SIZE]. */
  val CUSTOM_FONT_ENABLED: SettingsKey<Boolean> = uiKey("ui.customFont.enabled", default = false)

  /** Holds the scale of the IDE, where `1` is the normal size. */
  val IDE_SCALE: SettingsKey<Float> = uiKey("ui.ideScale", default = 1f, serializer = FloatSerializer)

  /** Holds the scale of the IDE in the presentation mode, or `null` when the user did not set one. */
  val PRESENTATION_MODE_IDE_SCALE: SettingsKey<Float?> =
    uiKey("ui.presentationModeIdeScale", default = null, serializer = FloatSerializer.nullable())

  /** Tells whether the tool windows show their buttons in one stripe. */
  val SINGLE_STRIPE: SettingsKey<Boolean> = uiKey("toolWindows.singleStripe", default = false, locality = KeyLocality.SHAREABLE)

  /** Lists every key of this object. [UISettingsKeysPromoter] registers each one. */
  val ALL_KEYS: List<SettingsKey<*>> = listOf(
    IDE_ANTIALIASING,
    EDITOR_ANTIALIASING,
    CUSTOM_FONT_FACE,
    CUSTOM_FONT_SIZE,
    CUSTOM_FONT_ENABLED,
    IDE_SCALE,
    PRESENTATION_MODE_IDE_SCALE,
    SINGLE_STRIPE,
  )

  /**
   * Launches an asynchronous update of these keys, for a caller that cannot suspend, such as an action on the EDT.
   *
   * @param update writes the keys, for example `KEY.setValue(value)`
   * @return the job of the update
   */
  fun updateAsync(update: suspend () -> Unit): Job = service<UISettingsKeysScope>().launchUpdate(update)

  /**
   * Launches an asynchronous write of [value] for [key], unless [key] already resolves to [value]. So the write never
   * stores a value that the key already has, such as its default.
   */
  fun <T> setAsync(key: SettingsKey<T>, value: T) {
    if (key.getValue() != value) {
      updateAsync { key.setValue(value) }
    }
  }

  /**
   * Creates the service of these keys, unless it exists. From its creation the service makes each change of these keys
   * call [UISettings.fireUISettingsChanged] on the EDT, so each [UISettingsListener] sees it. Changes that come close
   * together fire one event.
   */
  internal fun ensureServiceCreated() {
    service<UISettingsKeysScope>()
  }
}

/**
 * Holds the coroutine scope of the writes of [UISettingsKeys], of the forward of their changes, and of the calls of
 * the value listeners of [UISettings].
 */
@Service
internal class UISettingsKeysScope(val coroutineScope: CoroutineScope) {
  /**
   * Launches [update] in the modality of the caller.
   *
   * @return the job of [update]
   */
  fun launchUpdate(update: suspend () -> Unit): Job =
    coroutineScope.launch(ModalityState.defaultModalityState().asContextElement()) { update() }

  init {
    val keyChanges = Channel<Unit>(Channel.CONFLATED)
    // Any modality, because a modal dialog such as Settings can write a key.
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      for (change in keyChanges) {
        UISettings.getInstance().fireUISettingsChanged()
      }
    }
    val listenersDisposable = coroutineScope.asDisposable()
    for (key in UISettingsKeys.ALL_KEYS) {
      key.addListener(listenersDisposable, SettingsContext.app()) { _, _ -> keyChanges.trySend(Unit) }
    }
  }
}

/**
 * Creates an application key with a ready default value.
 *
 * @param serializer the serializer of the value, or `null` to infer it from [default]. A `null` or a [Float] default
 *   needs one.
 */
private fun <T> uiKey(
  name: String,
  default: T,
  serializer: Serializer<T>? = null,
  locality: KeyLocality = KeyLocality.ENVIRONMENTAL,
): SettingsKey<T> =
  SettingsKey(name = name,
              default = default,
              serializer = serializer,
              locality = locality,
              contextFilter = SettingsContextFilters.APP_ONLY)

/**
 * Creates an environmental application key of an antialiasing type.
 *
 * @param default returns the default value. A factory, because the default depends on the OS and on system properties.
 */
private fun antialiasingKey(name: String, default: () -> AntialiasingType): SettingsKey<AntialiasingType> =
  SettingsKey(name = name,
              serializer = enumSerializer<AntialiasingType>(),
              locality = KeyLocality.ENVIRONMENTAL,
              contextFilter = SettingsContextFilters.APP_ONLY,
              defaultFactory = { default() })

/**
 * Serializes a [Float] as a [SettingsValue.DoubleValue] through [DoubleSerializer]. The conversion goes through the
 * decimal text of the value, so `1.1f` is stored as `1.1`, not as `1.100000023841858`. It also reads a stored
 * [SettingsValue.IntValue] or [SettingsValue.LongValue], such as a hand-edited `2`.
 */
private object FloatSerializer : Serializer<Float> {
  override fun serialize(value: Float): SettingsValue = DoubleSerializer.serialize(value.toString().toDouble())

  override fun deserialize(value: SettingsValue): Float = when (value) {
    is SettingsValue.IntValue -> value.value.toFloat()
    is SettingsValue.LongValue -> value.value.toFloat()
    is SettingsValue.DoubleValue, SettingsValue.NullValue, is SettingsValue.StringValue, is SettingsValue.BooleanValue,
    is SettingsValue.ListValue, is SettingsValue.MapValue -> DoubleSerializer.deserialize(value).toFloat()
  }
}

/**
 * Registers the keys of [UISettingsKeys], so a write of them is not a missing promotion.
 * Registered as a `settings.v2.keyPromoter` in `intellij.platform.editor.ui.xml`.
 */
internal class UISettingsKeysPromoter : SettingsKeyPromoter {
  override fun promote(register: (SettingsKey<*>) -> Unit) {
    UISettingsKeys.ALL_KEYS.forEach(register)
  }
}

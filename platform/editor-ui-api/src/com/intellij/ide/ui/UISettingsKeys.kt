// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.platform.settings.v2.BooleanSerializer
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

/**
 * Holds the settings keys of the UI settings that [NotRoamableUiSettings] stores. Each key resolves only at the
 * application context. The fonts, the antialiasing and the scales depend on the display and the fonts of one machine,
 * so their keys are environmental. Only [SINGLE_STRIPE] is shareable.
 *
 * The default of each key reads the current value of the [NotRoamableUiSettings] component, so a key with no stored
 * value reads the component. The design expects the component to change only through the setters of [UISettings].
 * Only [UISettings] and [NotRoamableUiSettingsImport] use the keys. The other code reads and writes the settings
 * through [UISettings].
 */
internal object UISettingsKeys {
  /**
   * Holds the antialiasing of the text in the IDE. Without the component, the default is subpixel when the machine
   * supports it.
   */
  val IDE_ANTIALIASING: SettingsKey<AntialiasingType> = uiKey("ui.ideAntialiasing", enumSerializer(), NotRoamableUiSettings::ideAAType) {
    if (AntialiasingType.canUseSubpixelAAForIDE()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE
  }

  /**
   * Holds the antialiasing of the text in the editor. Without the component, the default is subpixel when the machine
   * supports it.
   */
  val EDITOR_ANTIALIASING: SettingsKey<AntialiasingType> = uiKey("ui.editorAntialiasing", enumSerializer(), NotRoamableUiSettings::editorAAType) {
    if (AntialiasingType.canUseSubpixelAAForEditor()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE
  }

  /** Holds the name of the custom font of the IDE, or `null` for the system font. */
  val CUSTOM_FONT_FACE: SettingsKey<String?> =
    uiKey("ui.customFont.face", StringSerializer.nullable(), { it.state.fontFace }) { null }

  /**
   * Holds the size of the custom font of the IDE. [UISettings] reads a size of `0` or less as the default font size.
   * The component holds `0` only when it has no stored state.
   */
  val CUSTOM_FONT_SIZE: SettingsKey<Float> = uiKey("ui.customFont.size", FloatSerializer, NotRoamableUiSettings::fontSize) { 0f }

  /** Tells whether the IDE uses the custom font of [CUSTOM_FONT_FACE] and [CUSTOM_FONT_SIZE]. */
  val CUSTOM_FONT_ENABLED: SettingsKey<Boolean> =
    uiKey("ui.customFont.enabled", BooleanSerializer, NotRoamableUiSettings::overrideLafFonts) { false }

  /** Holds the scale of the IDE, where `1` is the normal size. */
  val IDE_SCALE: SettingsKey<Float> = uiKey("ui.ideScale", FloatSerializer, NotRoamableUiSettings::ideScale) { 1f }

  /**
   * Holds the scale of the IDE in the presentation mode. A scale of the component of `0` or less reads as the default
   * scale of the presentation mode. The component holds such a scale only before [UISettings] loads its state.
   */
  val PRESENTATION_MODE_IDE_SCALE: SettingsKey<Float> =
    uiKey("ui.presentationModeIdeScale", FloatSerializer,
          { component -> component.presentationModeIdeScale.takeIf { it > 0 } ?: UISettingsUtils.defaultScale(isPresentation = true) }) {
      UISettingsUtils.defaultScale(isPresentation = true)
    }

  /** Tells whether the tool windows show their buttons in one stripe. */
  val SINGLE_STRIPE: SettingsKey<Boolean> =
    uiKey("toolWindows.singleStripe", BooleanSerializer, NotRoamableUiSettings::experimentalSingleStripe, KeyLocality.SHAREABLE) { false }

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
   * Queues a write of [value] for [key] and returns, for a caller that cannot suspend, such as an action on the EDT. The
   * writes apply one by one, in the order of the calls. A write stores nothing when [key] resolves to [value] at its
   * turn, so it never stores a value that the key already has, such as its default.
   */
  fun <T> setAsync(key: SettingsKey<T>, value: T) {
    service<UISettingsKeysScope>().writes.trySend {
      if (key.getValue() != value) {
        key.setValue(value)
      }
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
  /** Holds the queue of [UISettingsKeys.setAsync]. One coroutine applies the writes in their order. */
  val writes: SendChannel<suspend () -> Unit> = Channel<suspend () -> Unit>(Channel.UNLIMITED).also { queue ->
    coroutineScope.launch {
      for (write in queue) {
        // A child job, so a failed write is logged and the next writes still apply.
        coroutineScope.launch { write() }.join()
      }
    }
  }

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
 * Creates an application key whose default is the value of the [NotRoamableUiSettings] component.
 *
 * @param componentValue returns the value of the setting from the component
 * @param fallback returns the default when the component is not available, for example before the application starts
 *   or after it is disposed
 */
private fun <T> uiKey(
  name: String,
  serializer: Serializer<T>,
  componentValue: (NotRoamableUiSettings) -> T,
  locality: KeyLocality = KeyLocality.ENVIRONMENTAL,
  fallback: () -> T,
): SettingsKey<T> =
  SettingsKey(name = name,
              serializer = serializer,
              locality = locality,
              contextFilter = SettingsContextFilters.APP_ONLY,
              defaultFactory = {
                val component = ApplicationManager.getApplication()?.takeUnless { it.isDisposed }?.serviceOrNull<NotRoamableUiSettings>()
                if (component == null) fallback() else componentValue(component)
              })

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

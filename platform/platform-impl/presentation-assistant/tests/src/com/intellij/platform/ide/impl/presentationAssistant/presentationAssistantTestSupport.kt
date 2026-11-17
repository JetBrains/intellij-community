// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.PER_OS_SETTINGS
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.SettingsVault
import com.intellij.platform.settings.v2.impl.providers.JsonConfigFile
import com.intellij.platform.settings.v2.impl.providers.localityFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Holds the name of the team config of the application layer. It mirrors `AppLayerService.FILE_NAME` in
 * `community/platform/settings.v2/shared/src/com/intellij/platform/settings/v2/impl/providers/AppLayerService.kt`.
 */
private const val APP_CONFIG_FILE_NAME = "app.settings.json"

/** Holds the longest time that a poll waits for a write that the code under test launched. */
private val LAUNCHED_WRITE_TIMEOUT = 30.seconds

/**
 * Drops every stored value of the Presentation Assistant and puts the default state into its component. It removes the
 * keys of the Presentation Assistant from the team config of the application, `app.settings.json`, where the shareable
 * keys live, puts the [handWrittenEntries] there, and reloads the application context. Then the presenter follows the
 * keys again. The other entries of the config stay, such as the migration ledger, so no migration runs again after the
 * reload.
 *
 * @param handWrittenEntries the raw entries by their identities, as a user writes them into the config
 */
internal suspend fun replacePresentationAssistantConfig(handWrittenEntries: Map<String, SettingsValue> = emptyMap()) {
  val vault = SettingsVault.getInstance()
  val appContext = SettingsContext.app()
  vault.flush(appContext)
  // The per-OS storage holds only the keymap keys of the Presentation Assistant in this test application.
  val presentationAssistantIdentities = (PresentationAssistantSettings.ALL_KEYS + PER_OS_SETTINGS).map { it.identity }.toSet()
  withContext(Dispatchers.IO) {
    val config = Path.of(PathManager.getOptionsPath(), localityFileName(APP_CONFIG_FILE_NAME, LayerLocality.TEAM))
    val keptEntries = JsonConfigFile.read(config).values - presentationAssistantIdentities
    JsonConfigFile.write(config, SettingsValue.MapValue(keptEntries + handWrittenEntries))
  }
  vault.reload(appContext)
  serviceAsync<PresentationAssistant>().loadState(PresentationAssistantState())
}

/**
 * Polls [read] until it equals [expected], for up to [LAUNCHED_WRITE_TIMEOUT]. Then asserts the equality, so a real
 * mismatch still reports. A write that an action or a configurable launches lands later, so a test waits for it.
 */
internal suspend fun <T> awaitEquals(expected: T, read: suspend () -> T) {
  withTimeout(LAUNCHED_WRITE_TIMEOUT) {
    while (read() != expected) delay(10.milliseconds)
  }
  assertEquals(expected, read())
}

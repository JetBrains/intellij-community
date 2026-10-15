// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.collaboration.async.withInitial
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus

/**
 * Parses a host alias setting into a map from an alias host to the server.
 *
 * The value is a comma-separated list of `<alias>=<server>` and `<alias>` entries.
 * An entry without a server maps the alias to [defaultServer].
 * [parseServer] gets the trimmed server text and returns null for an invalid server.
 * The function ignores an entry with an invalid server.
 */
@ApiStatus.Internal
fun <S : ServerPath> parseServerHostAliases(value: String, defaultServer: S, parseServer: (String) -> S?): Map<String, S> =
  value.split(',').mapNotNull { entry ->
    val alias = entry.substringBefore('=').trim().lowercase()
    if (alias.isEmpty()) return@mapNotNull null
    val serverText = entry.substringAfter('=', "").trim()
    val server = if (serverText.isEmpty()) defaultServer else parseServer(serverText)
    server?.let { alias to it }
  }.toMap()

/**
 * Finds the server for [aliasServer], the server from the alias setting.
 * The function prefers the server with the same URI as [aliasServer].
 * Then it takes the first server with the same host, so the server URL comes from that server.
 * The function does not check the credentials for the server.
 * Without such a server, the function returns [aliasServer].
 */
@ApiStatus.Internal
fun <S : ServerPath> Collection<S>.findServerForAlias(aliasServer: S): S {
  val aliasUri = aliasServer.toURI()
  return find { it.toURI() == aliasUri } ?: find { it.toURI().host.equals(aliasUri.host, true) } ?: aliasServer
}

/**
 * Emits the host aliases from the advanced setting [settingId], and then emits them again after each change of the setting.
 * [parse] converts the setting value into the aliases.
 */
@ApiStatus.Internal
fun <S : ServerPath> serverHostAliasesFlow(settingId: String, parse: (String) -> Map<String, S>): Flow<Map<String, S>> =
  callbackFlow {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == settingId) trySend(Unit)
      }
    })
    awaitClose()
  }.withInitial(Unit).map { parse(AdvancedSettings.getString(settingId)) }

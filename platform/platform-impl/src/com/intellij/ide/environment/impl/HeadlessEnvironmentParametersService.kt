// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.CollectionType
import com.intellij.ide.environment.EnvironmentKey
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.*
import java.io.IOException

class HeadlessEnvironmentParametersService(scope: CoroutineScope) : BaseEnvironmentParametersService() {

  private val configurationFileModel : Deferred<Map<String, String>> = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    getModelFromFile()
  }

  private suspend fun scanForEnvironmentValue(key: EnvironmentKey, onError: () -> String?): String? {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Access to environment parameters in the IDE with UI must be delegated to the user")
    }
    checkKeyRegistered(key)

    val valueFromEnvironmentVariable = System.getProperty(key.id)
    if (valueFromEnvironmentVariable != null) {
      return valueFromEnvironmentVariable
    }

    val mapping = configurationFileModel.await()
    val valueFromConfigurationFile = mapping[key.id]
    if (valueFromConfigurationFile != null) {
      return valueFromConfigurationFile
    }

    val valueAsDefault = key.defaultValue.ifEmpty { null }
    if (valueAsDefault != null) {
      return valueAsDefault
    }
    return onError()
  }

  override suspend fun requestEnvironmentValue(key: EnvironmentKey): String? = scanForEnvironmentValue(key) {
    throw MissingEnvironmentKeyException(key)
  }

  override suspend fun getEnvironmentValueOrNull(key: EnvironmentKey): String? = scanForEnvironmentValue(key) {
    null
  }

  class MissingEnvironmentKeyException(val key: EnvironmentKey) : CancellationException(
    """Missing value for a key '${key.id}'
      |Usage:
      |${key.description.get()}
      |""".trimMargin())

  private class EnvironmentKeyEntry {
    @Suppress("unused")
    var description: Any? = null
    var key: String? = null
    var value: String? = null
  }

  private fun getModelFromFile(): Map<String, String> {
    val pathToFile = EnvironmentUtil.getPathToConfigurationFile() ?: return emptyMap()

    val objectMapper = ObjectMapper()
    val deserializedType: CollectionType = objectMapper.typeFactory.constructCollectionType(ArrayList::class.java, EnvironmentKeyEntry::class.java)

    val list : List<EnvironmentKeyEntry> = try {
      objectMapper.readValue(pathToFile.toFile(), deserializedType)
    } catch (e : IOException) {
      LOG.warn(e)
      return emptyMap()
    }

    val mapping = mutableMapOf<String, String>()
    for (jsonEntry in list) {
      val key = jsonEntry.key
      val value = jsonEntry.value
      if (key == null || value == null) {
        LOG.warn("Malformed JSON entry in $pathToFile: $key, $value")
        continue
      }
      if (value.isEmpty()) {
        continue
      }
      mapping[key] = value
    }
    return mapping
  }
}


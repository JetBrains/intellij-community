// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.CollectionType
import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentService
import com.intellij.ide.environment.description
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.configuration.HeadlessLogging
import kotlinx.coroutines.*
import java.io.IOException

class HeadlessEnvironmentService(scope: CoroutineScope) : BaseEnvironmentService() {

  private val configurationFileModel: Deferred<Map<String, String>> = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
    getModelFromFile()
  }

  override suspend fun getEnvironmentValue(key: EnvironmentKey): String {
    return getEnvironmentValueOrNull(key)
           ?: run {
             val throwable = MissingEnvironmentKeyException(key)
             HeadlessLogging.logFatalError(MissingEnvironmentKeyException(key))
             throw throwable
           }
  }

  override suspend fun getEnvironmentValue(key: EnvironmentKey, defaultValue: String): String {
    return getEnvironmentValueOrNull(key)
           ?: defaultValue
  }

  private suspend fun getEnvironmentValueOrNull(key: EnvironmentKey): String? {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Access to environment parameters in the IDE with UI must be delegated to the user")
    }
    checkKeyRegistered(key)

    val valueFromEnvironmentVariable = System.getProperty(key.id)
    if (valueFromEnvironmentVariable != null) {
      logger<EnvironmentService>().info("Obtained value for ${key.id} from a system property: $valueFromEnvironmentVariable")
      return valueFromEnvironmentVariable
    }

    val mapping = configurationFileModel.await()
    val valueFromConfigurationFile = mapping[key.id]
    if (valueFromConfigurationFile != null) {
      logger<EnvironmentService>().info("Obtained value for ${key.id} from the configuration file: $valueFromConfigurationFile")
      return valueFromConfigurationFile
    }
    return null
  }

  class MissingEnvironmentKeyException(val key: EnvironmentKey) : CancellationException(
    """Missing value for the environment key '${key.id}'
      |The value can be set as a system property (`-D${key.id}=<value>` in the VM options),
      |or as an entry in the JSON configuration file (the command-line starter `./idea.sh generateEnvironmentKeysFile`).
      |
      |Description of ${key.id}:
      |${key.description}
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
    val deserializedType: CollectionType = objectMapper.typeFactory.constructCollectionType(
      ArrayList::class.java, EnvironmentKeyEntry::class.java
    )

    val list: List<EnvironmentKeyEntry> = try {
      objectMapper.readValue(pathToFile.toFile(), deserializedType)
    }
    catch (e: IOException) {
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


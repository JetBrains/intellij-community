// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.ResourceUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction

@Service(Service.Level.APP)
class IconMapLoader {
  private val cachedResult = AtomicReference<Map<ClassLoader, Map<String, String>>>()

  suspend fun preloadIconMapping() {
    val size = IconMapperBean.EP_NAME.point.size()
    if (size == 0) {
      cachedResult.compareAndSet(null, emptyMap())
      return
    }

    coroutineScope {
      val channel = Channel<Pair<ByteArray, ExtensionPointName.LazyExtension<IconMapperBean>>>(Runtime.getRuntime().availableProcessors())
      launch {
        for (extension in IconMapperBean.EP_NAME.filterableLazySequence()) {
          launch(Dispatchers.IO) t@{
            val classLoader = extension.pluginDescriptor.pluginClassLoader ?: return@t
            val mappingFile = extension.instance?.mappingFile ?: return@t
            val data = ResourceUtil.getResourceAsBytes(mappingFile, classLoader)
            if (data == null) {
              logger<IconMapLoader>().error(PluginException("Cannot find $mappingFile", extension.pluginDescriptor.pluginId))
            }
            else {
              channel.send(data to extension)
            }
          }
        }
      }.invokeOnCompletion { channel.close() }

      val result = IdentityHashMap<ClassLoader, MutableMap<String, String>>(size)
      for ((data, extension) in channel) {
        try {
          val classLoader = extension.pluginDescriptor.pluginClassLoader!!
          val map = result.computeIfAbsent(classLoader) { HashMap() }
          val parser = JsonFactory().createParser(data)
          readDataFromJson(parser, map)
        }
        catch (e: Exception) {
          logger<IconMapLoader>().warn("Can't process ${extension.instance?.mappingFile}",
                                       PluginException(e, extension.pluginDescriptor.pluginId))
        }
      }

      // reduce memory usage
      result.replaceAll(BiFunction { _, value ->
        java.util.Map.copyOf(value)
      })

      cachedResult.compareAndSet(null, result)
    }
  }

  fun loadIconMapping(): Map<ClassLoader, Map<String, String>> {
    val result = cachedResult.getAndSet(null)
    if (result == null) {
      if (!ApplicationManager.getApplication().isUnitTestMode && !ApplicationManager.getApplication().isHeadlessEnvironment) {
        logger<IconMapLoader>().error("You must call IconMapLoader.preloadIconMapping() before calling loadIconMappings()")
      }
      return emptyMap()
    }
    else {
      return result
    }
  }
}

private fun readDataFromJson(parser: JsonParser, result: MutableMap<String, String>) {
  // simplify prefix calculating
  check(parser.nextToken() == JsonToken.START_OBJECT)

  val prefix = ArrayDeque<String>()
  val path = StringBuilder()
  var currentFieldName: String? = null
  while (true) {
    when (parser.nextToken()) {
      JsonToken.START_OBJECT -> {
        prefix.addLast(currentFieldName!!)
        currentFieldName = null
      }
      JsonToken.END_OBJECT -> {
        prefix.pollLast()
        currentFieldName = null
      }
      JsonToken.START_ARRAY -> {
        val fieldName = parser.currentName()
        while (true) {
          when (parser.nextToken()) {
            JsonToken.END_ARRAY -> break
            JsonToken.VALUE_STRING -> {
              if (!prefix.isEmpty()) {
                prefix.joinTo(buffer = path, separator = "/")
                path.append('/')
              }
              path.append(fieldName)
              result.put(parser.text, path.toString())
              path.setLength(0)
            }
            else -> {
              logError(parser)
            }
          }
        }
      }
      JsonToken.VALUE_STRING -> {
        if (!prefix.isEmpty()) {
          prefix.joinTo(buffer = path, separator = "/")
          path.append('/')
        }
        path.append(parser.currentName())
        result.put(parser.text, path.toString())
        path.setLength(0)
      }
      JsonToken.FIELD_NAME -> {
        currentFieldName = parser.currentName()
      }
      null -> return
      else -> {
        logError(parser)
      }
    }
  }
}

private fun logError(parser: JsonParser) {
  logger<IconMapLoader>().warn("JSON contains data in unsupported format (token=${parser.currentToken}): ${parser.currentValue()}")
}
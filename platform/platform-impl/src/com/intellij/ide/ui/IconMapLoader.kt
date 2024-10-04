// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.LazyExtension
import com.intellij.util.ResourceUtil
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction

@ApiStatus.Internal
@Service(Service.Level.APP)
class IconMapLoader {
  private val cachedResult = AtomicReference<Map<ClassLoader, Map<String, String>>>()

  suspend fun preloadIconMapping() {
    if (!IconMapperBean.EP_NAME.hasAnyExtensions()) {
      cachedResult.compareAndSet(null, emptyMap())
      return
    }

    val result = doLoadIconMapping()

    // reduce memory usage
    result.replaceAll(BiFunction { _, value ->
      java.util.Map.copyOf(value)
    })

    cachedResult.compareAndSet(null, result)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun doLoadIconMapping(): MutableMap<ClassLoader, MutableMap<String, String>> {
    val list = coroutineScope {
      val jsonFactory = JsonFactory()
      IconMapperBean.EP_NAME.filterableLazySequence().map { extension ->
        async {
          loadFromExtension(extension) { data, result ->
            readDataFromJson(jsonFactory.createParser(data), result)
          }
        }
      }
        .toList()
    }
      .mapNotNull { it.getCompleted() }

    val result = CollectionFactory.createWeakIdentityMap<ClassLoader, MutableMap<String, String>>(50, 0.5f)
    for (pair in list) {
      result.computeIfAbsent(pair.first) { HashMap() }.putAll(pair.second)
    }
    return result
  }

  private suspend fun loadFromExtension(extension: LazyExtension<IconMapperBean>,
                                        dataLoader: (data: ByteArray, result: HashMap<String, String>) -> Unit): Pair<ClassLoader, HashMap<String, String>>? {
    val classLoader = extension.pluginDescriptor.pluginClassLoader ?: return null
    val mappingFile = extension.instance?.mappingFile ?: return null
    val data = withContext(Dispatchers.IO) { ResourceUtil.getResourceAsBytes(mappingFile, classLoader) }
    if (data == null) {
      logger<IconMapLoader>().error(PluginException("Cannot find $mappingFile", extension.pluginDescriptor.pluginId))
      return null
    }

    val result = HashMap<String, String>()
    try {
      dataLoader(data, result)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logger<IconMapLoader>().warn("Can't process ${extension.instance?.mappingFile}",
                                   PluginException(e, extension.pluginDescriptor.pluginId))
    }
    return (classLoader to result)
  }

  fun loadIconMapping(): Map<ClassLoader, Map<String, String>>? {
    return cachedResult.getAndSet(null) ?: return null
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
              addWithCheck(result, parser, path)
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
        addWithCheck(result, parser, path)
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

private fun addWithCheck(result: MutableMap<String, String>, parser: JsonParser, path: StringBuilder) {
  val key = parser.text
  val p = path.toString()
  val oldValue = result.put(key, p)
  if (oldValue != null && oldValue != p) {
    logger<IconMapLoader>().error("Double icon mapping: $key -> $oldValue or $path")
  }
}

private fun logError(parser: JsonParser) {
  logger<IconMapLoader>().warn("JSON contains data in unsupported format (token=${parser.currentToken}): ${parser.currentValue()}")
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.search

import com.intellij.ide.plugins.PluginManagerCore.getPluginSet
import com.intellij.ide.ui.search.SearchableOptionsRegistrar.AdditionalLocationProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.ResourceUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CancellationException
import java.util.function.Predicate

internal class MySearchableOptionProcessor(private val stopWords: Set<String>) : SearchableOptionProcessor() {
  private val cache = HashSet<String>()
  val storage: MutableMap<CharSequence, LongArray> = CollectionFactory.createCharSequenceMap(20, 0.9f, true)
  @JvmField val identifierTable: IndexedCharsInterner = IndexedCharsInterner()

  override fun addOptions(text: String,
                          path: String?,
                          hit: String?,
                          configurableId: String,
                          configurableDisplayName: String?,
                          applyStemming: Boolean) {
    cache.clear()
    if (applyStemming) {
      SearchableOptionsRegistrarImpl.collectProcessedWords(text, cache, stopWords)
    }
    else {
      SearchableOptionsRegistrarImpl.collectProcessedWordsWithoutStemming(text, cache, stopWords)
    }
    putOptionWithHelpId(words = cache, id = configurableId, groupName = configurableDisplayName, hit = hit, path = path)
  }

  fun computeHighlightOptionToSynonym(): Map<Pair<String, String>, MutableSet<String>> {
    processSearchableOptions(this)
    return loadSynonyms()
  }

  internal fun putOptionsWithHelpId(configurable: ConfigurableEntry) {
    for (entry in configurable.entries) {
      putOptionWithHelpId(words = entry.words, id = configurable.id, groupName = configurable.name, hit = entry.hit, path = entry.path)
    }
  }

  private fun loadSynonyms(): Map<Pair<String, String>, MutableSet<String>> {
    val result = HashMap<Pair<String, String>, MutableSet<String>>()
    val root = JDOMUtil.load(ResourceUtil.getResourceAsStream(SearchableOptionsRegistrar::class.java.classLoader, "/search/", "synonyms.xml"))
    val cache = HashSet<String>()
    for (configurable in root.getChildren("configurable")) {
      val id = configurable.getAttributeValue("id") ?: continue
      val groupName = configurable.getAttributeValue("configurable_name")
      val synonyms = configurable.getChildren("synonym")
      for (synonymElement in synonyms) {
        val synonym = synonymElement.textNormalize ?: continue
        cache.clear()
        SearchableOptionsRegistrarImpl.collectProcessedWords(synonym, cache, stopWords)
        putOptionWithHelpId(words = cache, id = id, groupName = groupName, hit = synonym, path = null)
      }

      for (optionElement in configurable.getChildren("option")) {
        val option = optionElement.getAttributeValue("name")
        val list = optionElement.getChildren("synonym")
        for (synonymElement in list) {
          val synonym = synonymElement.textNormalize ?: continue
          cache.clear()
          SearchableOptionsRegistrarImpl.collectProcessedWords(synonym, cache, stopWords)
          putOptionWithHelpId(words = cache, id = id, groupName = groupName, hit = synonym, path = null)
          result.computeIfAbsent(Pair(option, id)) { HashSet() }.add(synonym)
        }
      }
    }
    return result
  }

  private fun putOptionWithHelpId(words: Collection<String>, id: String, groupName: String?, hit: String?, path: String?) {
    for (word in words) {
      if (stopWords.contains(word)) {
        return
      }

      val stopWord = PorterStemmerUtil.stem(word)
      if (stopWord == null || stopWords.contains(stopWord)) {
        return
      }

      var configs = storage.get(word)
      val packed = SearchableOptionsRegistrarImpl.pack(id, hit, path, groupName, identifierTable)
      if (configs == null) {
        configs = longArrayOf(packed)
      }
      else if (configs.indexOf(packed) == -1) {
        configs = ArrayUtil.append(configs, packed)
      }
      storage.put(word, configs!!)
    }
  }
}

@Serializable
@Internal
data class SearchableOptionEntry(
  @JvmField val hit: String?,
  @JvmField val words: List<String>,
  @JvmField val path: String? = null,
)

@Serializable
@Internal
data class ConfigurableEntry(
  @JvmField val id: String,
  @JvmField val name: String,
  @JvmField val entries: MutableList<SearchableOptionEntry> = mutableListOf(),
)

private val LOCATION_EP_NAME = ExtensionPointName<AdditionalLocationProvider>("com.intellij.search.additionalOptionsLocation")

@OptIn(ExperimentalSerializationApi::class)
private fun processSearchableOptions(processor: MySearchableOptionProcessor) {
  val name = SearchableOptionsRegistrar.getSearchableOptionsName()
  val xmlName = "$name.xml"

  val visited = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())
  val serializer = ConfigurableEntry.serializer()
  for (module in getPluginSet().getEnabledModules()) {
    val classLoader = module.pluginClassLoader
    if (classLoader !is UrlClassLoader || !visited.add(classLoader)) {
      continue
    }

    val classifier = if (module.moduleName == null) "p-${module.pluginId.idString}" else "m-${module.moduleName}"
    classLoader.getResourceAsBytes("$classifier-$name.json", false)?.let { data ->
      try {
        decodeFromJsonFormat(data, serializer).forEach {
          processor.putOptionsWithHelpId(it)
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        throw RuntimeException("Can't parse searchable options $name for plugin ${module.pluginId}", e)
      }

      // if the data is found in JSON format, there's no need to search in XML
      return
    }

    classLoader.processResources("search", Predicate { it.endsWith(xmlName) }) { _, stream ->
      try {
        readInXml(root = readXmlAsModel(stream), processor)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        throw RuntimeException("Can't parse searchable options $name for plugin ${module.pluginId}", e)
      }
    }
  }

  // process additional locations
  LOCATION_EP_NAME.forEachExtensionSafe { provider ->
    val additionalLocation = provider.additionalLocation ?: return@forEachExtensionSafe
    if (Files.isDirectory(additionalLocation)) {
      Files.list(additionalLocation).use { stream ->
        stream
          .forEach { file ->
            val fileName = file.fileName.toString()
            try {
              if (fileName.endsWith(xmlName)) {
                readInXml(root = readXmlAsModel(file), processor = processor)
              }
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (e: Throwable) {
              throw RuntimeException("Can't parse searchable options $name", e)
            }
          }
      }
    }
  }
}

@Internal
@VisibleForTesting
@OptIn(ExperimentalSerializationApi::class)
fun decodeFromJsonFormat(data: ByteArray, serializer: KSerializer<ConfigurableEntry>): Sequence<ConfigurableEntry> {
  return Json.decodeToSequence(ByteArrayInputStream(data), serializer, DecodeSequenceMode.WHITESPACE_SEPARATED)
}

private fun readInXml(root: XmlElement, processor: MySearchableOptionProcessor) {
  for (configurable in root.children("configurable")) {
    val entry = ConfigurableEntry(
      id = configurable.getAttributeValue("id") ?: continue,
      name = configurable.getAttributeValue("configurable_name") ?: continue,
      entries = configurable.children("option").mapNotNullTo(ArrayList()) { optionElement ->
        SearchableOptionEntry(
          hit = optionElement.getAttributeValue("hit") ?: return@mapNotNullTo null,
          words = listOfNotNull(optionElement.getAttributeValue("name")),
          path = optionElement.getAttributeValue("path"),
        )
      },
    )
    processor.putOptionsWithHelpId(entry)
  }
}
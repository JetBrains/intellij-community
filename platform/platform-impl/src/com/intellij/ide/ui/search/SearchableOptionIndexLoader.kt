// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.search

import com.intellij.DynamicBundle
import com.intellij.IntelliJResourceBundle
import com.intellij._doResolveBundle
import com.intellij.ide.plugins.PluginManagerCore.getPluginSet
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.ui.search.SearchableOptionsRegistrar.AdditionalLocationProvider
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ResourceUtil
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
import java.util.stream.Stream

private val LOG = logger<MySearchableOptionProcessor>()

internal class MySearchableOptionProcessor(private val stopWords: Set<String>) : SearchableOptionProcessor() {
  private val cache = HashSet<String>()
  @JvmField val storage: MutableMap<String, LongArray> = HashMap()
  @JvmField val identifierTable: IndexedCharsInterner = IndexedCharsInterner()

  override fun addOptions(
    text: String,
    path: String?,
    hit: String?,
    configurableId: String,
    configurableDisplayName: String?,
    applyStemming: Boolean,
  ) {
    cache.clear()
    if (applyStemming) {
      collectProcessedWords(text, cache, stopWords)
    }
    else {
      collectProcessedWordsWithoutStemming(text, cache, stopWords)
    }
    putOptionWithHelpId(words = cache, id = configurableId, groupName = configurableDisplayName, hit = hit, path = path)
  }

  fun computeHighlightOptionToSynonym(): Map<Pair<String, String>, MutableSet<String>> {
    processSearchableOptions(processor = this)
    return loadSynonyms()
  }

  private fun loadSynonyms(): Map<Pair<String, String>, MutableSet<String>> {
    val result = HashMap<Pair<String, String>, MutableSet<String>>()
    val root = JDOMUtil.load(ResourceUtil.getResourceAsStream(SearchableOptionsRegistrar::class.java.classLoader, "search", "synonyms.xml"))
    val cache = HashSet<String>()
    for (configurable in root.getChildren("configurable")) {
      val id = configurable.getAttributeValue("id") ?: continue
      val groupName = configurable.getAttributeValue("configurable_name")
      val synonyms = configurable.getChildren("synonym")
      for (synonymElement in synonyms) {
        val synonym = synonymElement.textNormalize ?: continue
        cache.clear()
        collectProcessedWords(synonym, cache, stopWords)
        putOptionWithHelpId(words = cache, id = id, groupName = groupName, hit = synonym, path = null)
      }

      for (optionElement in configurable.getChildren("option")) {
        val option = optionElement.getAttributeValue("name")
        val list = optionElement.getChildren("synonym")
        for (synonymElement in list) {
          val synonym = synonymElement.textNormalize ?: continue
          cache.clear()
          collectProcessedWords(synonym, cache, stopWords)
          putOptionWithHelpId(words = cache, id = id, groupName = groupName, hit = synonym, path = null)
          result.computeIfAbsent(Pair(option, id)) { HashSet() }.add(synonym)
        }
      }
    }
    return result
  }

  internal fun putOptionWithHelpId(words: Iterable<String>, id: String, groupName: String?, hit: String?, path: String?) {
    for (word in words) {
      if (stopWords.contains(word)) {
        continue
      }

      val stopWord = PorterStemmerUtil.stem(word)
      if (stopWord == null || stopWords.contains(stopWord)) {
        continue
      }

      val configs = storage.get(word)
      val packed = SearchableOptionsRegistrarImpl.pack(id, hit, path, groupName, identifierTable)
      if (configs == null) {
        storage.put(word, longArrayOf(packed))
      }
      else if (!configs.contains(packed)) {
        storage.put(word, configs + packed)
      }
    }
  }
}

@Serializable
@Internal
data class ConfigurableEntry(
  @JvmField val id: String,
  @JvmField val name: String,
  @JvmField val entries: MutableList<SearchableOptionEntry> = mutableListOf(),
)

@Internal
val INDEX_ENTRY_REGEXP: Regex = Regex("""\|b\|([^|]+)\|k\|([^|]+)\|""")

private val LOCATION_EP_NAME = ExtensionPointName<AdditionalLocationProvider>("com.intellij.search.additionalOptionsLocation")

private fun getMessageByCoordinate(s: String, classLoader: ClassLoader, locale: Locale): String {
  val matches = INDEX_ENTRY_REGEXP.findAll(s)
  if (matches.none()) {
    return s
  }

  val result = StringBuilder()
  for (match in matches) {
    val groups = match.groups
    val bundle = findBundle(classLoader = classLoader, locale = locale, bundlePath = groups[1]!!.value) ?: continue
    if (bundle !is IntelliJResourceBundle) {
      // todo we should fix resolveResourceBundleWithFallback and do not try to load bundle if we cannot find it in localization plugin
      LOG.debug { "Unexpected bundle type due to fallback: ${bundle.javaClass.name}" }
      continue
    }

    val messageKey = groups[2]!!.value
    val resolvedMessage = bundle.getMessageOrNull(messageKey) ?: continue
    result.append(resolvedMessage)
  }
  return result.toString()
}

private fun findBundle(classLoader: ClassLoader, locale: Locale, bundlePath: String): ResourceBundle? {
  try {
    return _doResolveBundle(loader = classLoader, locale = locale, pathToBundle = bundlePath)
  }
  catch (_: MissingResourceException) {
    if (classLoader is PluginAwareClassLoader) {
      return null
    }

    val visited = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())
    visited.add(classLoader)
    for (extension in DynamicBundle.LanguageBundleEP.EP_NAME.filterableLazySequence()) {
      visited.add(extension.pluginDescriptor.classLoader)
    }
    for (descriptor in getPluginSet().getEnabledModules()) {
      if (!visited.add(descriptor.classLoader)) {
        continue
      }

      try {
        val b = _doResolveBundle(loader = descriptor.classLoader, locale = locale, pathToBundle = bundlePath)
        return b
      }
      catch (_: MissingResourceException) {
      }
    }
    return null
  }
}

private fun processSearchableOptions(processor: MySearchableOptionProcessor) {
  val visited = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())
  val serializer = ConfigurableEntry.serializer()
  for (module in getPluginSet().getEnabledModules()) {
    val classLoader = module.pluginClassLoader
    if (classLoader !is UrlClassLoader || !visited.add(classLoader)) {
      continue
    }

    val classifier = if (module.moduleName == null) "p-${module.pluginId.idString}" else "m-${module.moduleName}"

    val fileName = "$classifier-${SearchableOptionsRegistrar.SEARCHABLE_OPTIONS_XML_NAME}.json"
    val data = classLoader.getResourceAsBytes(fileName, false)
    if (data != null) {
      val locale = LocalizationUtil.getLocaleOrNullForDefault()
      val localeSpecificLoader = LocalizationUtil.getPluginClassLoader()
      try {
        for (item in decodeFromJsonFormat(data, serializer)) {
          doRegisterIndex(
            item = item,
            classLoader = classLoader,
            locale = locale,
            processor = processor,
            localeSpecificLoader = localeSpecificLoader,
          )
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        throw RuntimeException("Can't parse searchable options $fileName for plugin ${module.pluginId}", e)
      }
      // if the data is found in JSON format, there's no need to search in XML
      continue
    }

    val xmlName = "${SearchableOptionsRegistrar.SEARCHABLE_OPTIONS_XML_NAME}.xml"
    classLoader.processResources("search", Predicate { it.endsWith(xmlName) }) { _, stream ->
      try {
        readInXml(root = readXmlAsModel(stream), processor)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        throw RuntimeException("Can't parse searchable options $fileName for plugin ${module.pluginId}", e)
      }
    }
  }

  // process additional locations
  val xmlName = "${SearchableOptionsRegistrar.SEARCHABLE_OPTIONS_XML_NAME}.xml"
  LOCATION_EP_NAME.forEachExtensionSafe { provider ->
    val additionalLocation = provider.additionalLocation ?: return@forEachExtensionSafe
    if (Files.isDirectory(additionalLocation)) {
      Files.list(additionalLocation).use { stream ->
        stream.forEach { file ->
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
            throw RuntimeException("Can't parse searchable options $xmlName", e)
          }
        }
      }
    }
  }
}

private fun doRegisterIndex(
  item: ConfigurableEntry,
  classLoader: ClassLoader,
  locale: Locale?,
  processor: MySearchableOptionProcessor,
  localeSpecificLoader: ClassLoader?,
) {
  val groupName = getMessageByCoordinate(s = item.name, classLoader = localeSpecificLoader ?: classLoader, locale = locale ?: Locale.ROOT)
  val id = getMessageByCoordinate(s = item.id, classLoader = localeSpecificLoader ?: classLoader, locale = locale ?: Locale.ROOT)
  for (entry in item.entries) {
    processor.putOptionWithHelpId(
      words = Iterable {
        val h1 = getMessageByCoordinate(entry.hit, classLoader, Locale.ROOT).lowercase(Locale.ROOT)
        val s1 = splitToWordsWithoutStemmingAndStopWords(h1)
        if (locale == null) {
          s1.iterator()
        }
        else {
          val h2 = getMessageByCoordinate(entry.hit, localeSpecificLoader!!, locale).lowercase(locale)
          val s2 = splitToWordsWithoutStemmingAndStopWords(h2)
          Stream.concat(s2, s1).iterator()
        }
      },
      id = id,
      groupName = groupName,
      hit = getMessageByCoordinate(s = entry.hit, classLoader = localeSpecificLoader ?: classLoader, locale = locale ?: Locale.ROOT),
      path = entry.path?.let {
        getMessageByCoordinate(s = it, classLoader = localeSpecificLoader ?: classLoader, locale = locale ?: Locale.ROOT)
      },
    )
  }
}

private val json = Json { ignoreUnknownKeys = true }

@Internal
@VisibleForTesting
@OptIn(ExperimentalSerializationApi::class)
fun decodeFromJsonFormat(data: ByteArray, serializer: KSerializer<ConfigurableEntry>): Sequence<ConfigurableEntry> {
  return json.decodeToSequence(ByteArrayInputStream(data), serializer, DecodeSequenceMode.WHITESPACE_SEPARATED)
}

private fun readInXml(root: XmlElement, processor: MySearchableOptionProcessor) {
  for (configurable in root.children("configurable")) {
    val id = configurable.getAttributeValue("id") ?: continue
    val name = configurable.getAttributeValue("configurable_name") ?: continue

    for (optionElement in configurable.children("option")) {
      val text = optionElement.getAttributeValue("hit") ?: continue
      processor.putOptionWithHelpId(
        words = listOfNotNull(optionElement.getAttributeValue("name")),
        id = id,
        groupName = name,
        hit = text,
        path = optionElement.getAttributeValue("path"),
      )
    }
  }
}
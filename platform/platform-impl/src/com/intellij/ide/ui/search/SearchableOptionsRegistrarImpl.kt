// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.ide.ui.search

import com.intellij.CommonBundle
import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.search.SearchableOptionsRegistrar.SETTINGS_GROUP_SEPARATOR
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ResourceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.regex.Pattern
import java.util.stream.Stream
import javax.swing.event.DocumentEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

private val LOG = logger<SearchableOptionsRegistrarImpl>()
private val EP_NAME = ExtensionPointName<SearchableOptionContributor>("com.intellij.search.optionContributor")
internal val WORD_SEPARATOR_CHARS: @NonNls Pattern = Pattern.compile("[^-\\pL\\d#+]+")

@ApiStatus.Internal
class SearchableOptionsRegistrarImpl(private val coroutineScope: CoroutineScope) : SearchableOptionsRegistrar() {
  private val stopWords: Set<String>

  @Volatile
  private var highlightOptionToSynonym = emptyMap<Pair<String, String>, MutableSet<String>>()

  // option => array of packed OptionDescriptor
  @Volatile
  private var storage: Deferred<Map<String, LongArray>>? = null

  @Volatile
  private var identifierTable: IndexedCharsInterner? = null

  init {
    val app = ApplicationManager.getApplication()
    if (app.isCommandLine() || app.isHeadlessEnvironment()) {
      stopWords = emptySet()
    }
    else {
      stopWords = loadStopWords()
      startLoading()

      app.getMessageBus().simpleConnect().subscribe<DynamicPluginListener>(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
          dropStorage()
        }

        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          dropStorage()
        }
      })
    }
  }

  companion object {
    /**
     * @return XYZT:64 bits where
     * X:16 bits - id of the interned groupName
     * Y:16 bits - id of the interned id
     * Z:16 bits - id of the interned hit
     * T:16 bits - id of the interned path
     */
    @Suppress("SpellCheckingInspection", "LocalVariableName")
    internal fun pack(
      id: String,
      hit: String?,
      path: String?,
      groupName: String?,
      identifierTable: IndexedCharsInterner,
    ): Long {
      val _id = identifierTable.toId(id.trim()).toLong()
      val _hit = if (hit == null) Short.MAX_VALUE.toLong() else identifierTable.toId(hit.trim()).toLong()
      val _path = if (path == null) Short.MAX_VALUE.toLong() else identifierTable.toId(path.trim()).toLong()
      val _groupName = if (groupName == null) Short.MAX_VALUE.toLong() else identifierTable.toId(groupName.trim()).toLong()
      assert(_id >= 0 && _id < Short.MAX_VALUE)
      assert(_hit >= 0 && _hit <= Short.MAX_VALUE)
      assert(_path >= 0 && _path <= Short.MAX_VALUE)
      assert(_groupName >= 0 && _groupName <= Short.MAX_VALUE)
      return _groupName shl 48 or (_id shl 32) or (_hit shl 16) or _path /* << 0*/
    }
  }

  @Synchronized
  private fun dropStorage() {
    storage?.cancel()
    storage = null
    identifierTable = null
    highlightOptionToSynonym = emptyMap()
  }

  fun isInitialized(): Boolean = storage?.isCompleted == true

  @TestOnly
  fun initializeBlocking() {
    @Suppress("SSBasedInspection")
    runBlocking(Dispatchers.Default) {
      initialize()
    }
  }

  @Synchronized
  private fun startLoading(): Deferred<Map<String, LongArray>> {
    storage?.let {
      return it
    }

    val job = coroutineScope.async(Dispatchers.IO) {
      doInitialize()
    }
    storage = job
    return job
  }

  @ApiStatus.Internal
   suspend fun initialize() {
     (storage ?: startLoading()).await()
  }

  private suspend fun doInitialize(): Map<String, LongArray> {
    val processor = MySearchableOptionProcessor(stopWords)
    try {
      for (extension in EP_NAME.filterableLazySequence()) {
        coroutineContext.ensureActive()

        try {
          extension.instance?.contribute(processor)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(PluginException(e, extension.pluginDescriptor.pluginId))
        }
      }

      coroutineContext.ensureActive()

      highlightOptionToSynonym = processor.computeHighlightOptionToSynonym()
      identifierTable = processor.identifierTable

      return processor.storage
    }
    catch (e: CancellationException) {
      LOG.warn("=== Search storage init canceled ===")
      throw e
    }
  }

  /**
   * Retrieves all searchable option names.
   */
  @ApiStatus.Internal
  fun getAllOptionNames(): Set<String> = storage?.takeIf { it.isCompleted }?.getCompleted()?.keys ?: emptySet()

  @ApiStatus.Internal
  fun getStorage(): Sequence<Pair<String, Sequence<OptionDescription>>> {
    val storage = storage?.takeIf { it.isCompleted }?.getCompleted() ?: return emptySequence()
    return storage.asSequence().map { (k, v) -> k to v.asSequence().map { unpack(it)} }
  }

  @Suppress("LocalVariableName")
  private fun unpack(data: Long): OptionDescription {
    val _groupName = (data shr 48 and 0xffffL).toInt()
    val _id = (data shr 32 and 0xffffL).toInt()
    val _hit = (data shr 16 and 0xffffL).toInt()
    val _path = (data and 0xffffL).toInt()
    assert( /*_id >= 0 && */_id < Short.Companion.MAX_VALUE)
    assert( /*_hit >= 0 && */_hit <= Short.Companion.MAX_VALUE)
    assert( /*_path >= 0 && */_path <= Short.Companion.MAX_VALUE)
    assert( /*_groupName >= 0 && */_groupName <= Short.Companion.MAX_VALUE)

    val identifierTable = identifierTable!!
    val groupName = if (_groupName == Short.MAX_VALUE.toInt()) null else identifierTable.fromId(_groupName)
    val configurableId = identifierTable.fromId(_id)
    val hit = if (_hit == Short.MAX_VALUE.toInt()) null else identifierTable.fromId(_hit)
    val path = if (_path == Short.MAX_VALUE.toInt()) null else identifierTable.fromId(_path)

    return OptionDescription(_option = null, configurableId = configurableId, hit = hit, path = path, groupName = groupName)
  }

  @Suppress("LocalVariableName")
  private fun unpackConfigurableId(data: Long): String {
    val _id = (data shr 32 and 0xffffL).toInt()
    assert(_id < Short.Companion.MAX_VALUE)
    return identifierTable!!.fromId(_id)
  }

  override fun getConfigurables(
    groups: List<ConfigurableGroup>,
    type: DocumentEvent.EventType?,
    previouslyFiltered: MutableSet<out Configurable>?,
    option: String,
    project: Project?,
  ): ConfigurableHit {
    var previouslyFiltered = previouslyFiltered
    if (previouslyFiltered == null || previouslyFiltered.isEmpty()) {
      previouslyFiltered = null
    }

    @Suppress("HardCodedStringLiteral")
    val optionToCheck = option.trim().lowercase()

    findGroupsByPath(groups, optionToCheck)?.let {
      return it
    }

    val effectiveConfigurables = LinkedHashSet<Configurable>()
    if (previouslyFiltered == null) {
      for (group in groups) {
        processExpandedGroups(group, effectiveConfigurables)
      }
    }
    else {
      effectiveConfigurables.addAll(previouslyFiltered)
    }

    val nameHits = LinkedHashSet<Configurable>()
    val nameFullHits = LinkedHashSet<Configurable>()

    val options = getProcessedWordsWithoutStemming(optionToCheck)
    if (options.isEmpty()) {
      for (each in effectiveConfigurables) {
        if (each.getDisplayName() != null) {
          nameHits.add(each)
          nameFullHits.add(each)
        }
      }
    }
    else {
      for (each in effectiveConfigurables) {
        val displayName = each.getDisplayName()
        if (displayName != null && displayName.contains(optionToCheck, ignoreCase = true)) {
          nameFullHits.add(each)
          nameHits.add(each)
        }
      }
    }

    // operate with substring
    val descriptionOptions = HashSet<String>()
    if (options.isEmpty()) {
      val components = WORD_SEPARATOR_CHARS.split(optionToCheck)
      if (components.isEmpty()) {
        descriptionOptions.add(option)
      }
      else {
        descriptionOptions.addAll(components)
      }
    }
    else {
      descriptionOptions.addAll(options)
    }

    val foundIds = findConfigurablesByDescriptions(descriptionOptions)
    if (foundIds == null) {
      return ConfigurableHit(
        nameHits = nameHits,
        nameFullHits = nameFullHits,
        contentHits = emptyList(),
        spotlightFilter = option,
      )
    }

    val contentHits = filterById(effectiveConfigurables, foundIds)

    if (type == DocumentEvent.EventType.CHANGE && previouslyFiltered != null && effectiveConfigurables.size == contentHits.size) {
      return getConfigurables(
        groups = groups,
        type = DocumentEvent.EventType.CHANGE,
        previouslyFiltered = null,
        option = option,
        project = project,
      )
    }
    else {
      return ConfigurableHit(
        nameHits = nameHits,
        nameFullHits = nameFullHits,
        contentHits = contentHits,
        spotlightFilter = option,
      )
    }
  }

  private fun findConfigurablesByDescriptions(descriptionOptions: Set<String>): MutableSet<String>? {
    var result: MutableSet<String>? = null
    for (prefix in descriptionOptions) {
      val ids = HashSet<String>()
      for (longs in findAcceptablePackedDescriptions(prefix) ?: return null) {
        for (l in longs) {
          ids.add(unpackConfigurableId(l))
        }
      }
      if (result == null) {
        result = ids
      }
      else {
        result.retainAll(ids)
      }
    }
    return result
  }

  fun findAcceptableDescriptions(prefix: String): Sequence<OptionDescription>? {
    return findAcceptablePackedDescriptions(prefix)?.flatMap { data -> data.asSequence().map { unpack(it)} }
  }

  private fun findAcceptablePackedDescriptions(prefix: String): Sequence<LongArray>? {
    val storage = storage?.takeIf { it.isCompleted }?.getCompleted()
    if (storage == null) {
      LOG.error("Not yet initialized")
      return null
    }

    val stemmedPrefix = PorterStemmerUtil.stem(prefix)
    if (stemmedPrefix == null || stemmedPrefix.isBlank()) {
      return null
    }

    return sequence {
      for (entry in storage.entries) {
        val option = entry.key
        if (!option.startsWith(prefix) && !option.startsWith(stemmedPrefix)) {
          val stemmedOption = PorterStemmerUtil.stem(option)
          if (stemmedOption != null && !stemmedOption.startsWith(prefix) && !stemmedOption.startsWith(stemmedPrefix)) {
            continue
          }
        }

        yield(entry.value)
      }
    }
  }

  private fun getOptionDescriptionsByWords(
    configurable: SearchableConfigurable,
    words: MutableSet<String>,
  ): MutableSet<OptionDescription>? {
    var path: MutableSet<OptionDescription>? = null
    for (word in words) {
      val configs = findAcceptableDescriptions(word) ?: return null
      val paths = HashSet<OptionDescription>()
      for (config in configs) {
        if (config.configurableId == configurable.getId()) {
          paths.add(config)
        }
      }
      if (path == null) {
        path = paths
      }
      path.retainAll(paths)
    }
    return path
  }

  override fun getInnerPaths(configurable: SearchableConfigurable, option: String): MutableSet<String> {
    val words = getProcessedWordsWithoutStemming(option)
    val path = getOptionDescriptionsByWords(configurable, words)

    if (path == null || path.isEmpty()) {
      return mutableSetOf<String>()
    }

    val resultSet = HashSet<String>()
    var theOnlyResult: OptionDescription? = null
    for (description in path) {
      val hit = description.hit
      if (hit != null) {
        var theBest = true
        for (word in words) {
          if (!hit.contains(word, ignoreCase = true)) {
            theBest = false
            break
          }
        }
        if (theBest) {
          val p = description.path
          if (p != null) {
            resultSet.add(p)
          }
        }
      }
      theOnlyResult = description
    }

    if (resultSet.isEmpty()) {
      val p = theOnlyResult!!.path
      if (p != null) {
        resultSet.add(p)
      }
    }

    return resultSet
  }

  override fun isStopWord(word: String?): Boolean = stopWords.contains(word)

  override fun getProcessedWordsWithoutStemming(text: String): MutableSet<String> {
    val result = HashSet<String>()
    collectProcessedWordsWithoutStemming(text = text, result = result, stopWords = stopWords)
    return result
  }

  override fun getProcessedWords(text: String): Set<String> {
    val result = HashSet<String>()
    collectProcessedWords(text, result, stopWords)
    return result
  }

  override fun replaceSynonyms(options: MutableSet<String>, configurable: SearchableConfigurable): MutableSet<String> {
    if (highlightOptionToSynonym.isEmpty()) {
      return options
    }

    val result = HashSet<String>(options)
    for (option in options) {
      val synonyms = highlightOptionToSynonym.get(option to configurable.getId())
      if (synonyms == null) {
        result.add(option)
      }
      else {
        result.addAll(synonyms)
      }
    }
    return result
  }
}

private fun loadStopWords(): Set<String> {
  try {
    // stop words
    val stream = ResourceUtil.getResourceAsStream(SearchableOptionsRegistrarImpl::class.java.getClassLoader(), "search", "ignore.txt")
                 ?: throw IOException("Broken installation: IDE does not provide /search/ignore.txt")
    return stream.reader().useLines { lines ->
      lines.filter { it.isNotEmpty() }.toHashSet()
    }
  }
  catch (e: IOException) {
    LOG.error(e)
    return emptySet()
  }
}

private fun filterById(configurables: Set<Configurable>, configurableIds: Set<String>): List<Configurable> {
  return configurables.filter { configurable ->
    if (configurable is SearchableConfigurable && configurableIds.contains(configurable.getId())) {
      return@filter true
    }

    if (configurable is SearchableConfigurable.Merged) {
      for (mergedConfigurable in configurable.getMergedConfigurables()) {
        if (mergedConfigurable is SearchableConfigurable && configurableIds.contains(mergedConfigurable.getId())) {
          return@filter true
        }
      }
    }
    false
  }
}

private fun findGroupsByPath(groups: List<ConfigurableGroup>, path: String): ConfigurableHit? {
  val split = parseSettingsPath(path)
  if (split.isNullOrEmpty()) {
    return null
  }

  val root = groups.singleOrNull()
  var topLevel: List<Configurable>
  if (root is SearchableConfigurable && (root as SearchableConfigurable).getId() == ConfigurableExtensionPointUtil.ROOT_CONFIGURABLE_ID) {
    topLevel = root.getConfigurables().toList()
  }
  else {
    topLevel = groups.filterIsInstance<Configurable>()
  }

  var current = topLevel
  var lastMatched: Configurable? = null
  var lastMatchedIndex = -1

  for (i in split.indices) {
    val option = split.get(i)
    val matched = current.find { it.getDisplayName().equals(option, ignoreCase = true) } ?: break

    lastMatched = matched
    lastMatchedIndex = i

    if (matched is Configurable.Composite) {
      current = matched.getConfigurables().asList()
    }
    else {
      break
    }
  }
  if (lastMatched == null) {
    return null
  }

  val spotlightFilter = if (lastMatchedIndex + 1 < split.size) {
    split.subList(lastMatchedIndex + 1, split.size).joinToString(separator = " ")
  }
  else {
    ""
  }

  val hits = listOf(lastMatched)
  return ConfigurableHit(nameHits = hits, nameFullHits = hits, contentHits = hits, spotlightFilter = spotlightFilter)
}

private fun parseSettingsPath(path: String): List<String>? {
  if (!path.contains(SETTINGS_GROUP_SEPARATOR)) {
    return null
  }

  var split = path.splitToSequence(SETTINGS_GROUP_SEPARATOR).map { it.trim() }.toList()
  val prefixes = listOf<@NlsSafe String>(
    "IntelliJ IDEA",
    ApplicationNamesInfo.getInstance().fullProductName,
    "Settings",
    "Preferences",
    "File | Settings",
    CommonBundle.message("action.settings.path"),
    CommonBundle.message("action.settings.path.mac"),
    CommonBundle.message("action.settings.path.macOS.ventura"),
  )
  for (prefix in prefixes) {
    split = skipPrefixIfNeeded(prefix, split)
  }
  return split
}

private fun skipPrefixIfNeeded(prefix: String, split: List<String>): List<String> {
  if (split.isEmpty()) {
    return split
  }

  val prefixSplit = prefix.splitToSequence(SETTINGS_GROUP_SEPARATOR).map { it.trim() }.toList()
  if (split.size < prefixSplit.size) {
    return split
  }

  for (i in prefixSplit.indices) {
    if (!split.get(i).equals(prefixSplit.get(i), ignoreCase = true)) {
      return split
    }
  }

  return split.subList(prefixSplit.size, split.size)
}

internal fun splitToWordsWithoutStemmingAndStopWords(text: String): Stream<String> = WORD_SEPARATOR_CHARS.splitAsStream(text)

internal fun collectProcessedWordsWithoutStemming(
  text: String,
  result: MutableSet<String>,
  stopWords: Set<String>,
) {
  for (opt in WORD_SEPARATOR_CHARS.split(text.lowercase())) {
    if (!stopWords.contains(opt) && !stopWords.contains(PorterStemmerUtil.stem(opt))) {
      result.add(opt)
    }
  }
}

internal fun collectProcessedWords(text: String, result: MutableSet<String>, stopWords: Set<String>) {
  for (opt in WORD_SEPARATOR_CHARS.split(text.lowercase())) {
    if (!stopWords.contains(opt)) {
      result.add(PorterStemmerUtil.stem(opt) ?: continue)
    }
  }
}

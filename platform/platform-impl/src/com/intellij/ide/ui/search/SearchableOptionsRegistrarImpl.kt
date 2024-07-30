// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

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
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.util.ResourceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import java.util.stream.Stream
import javax.swing.event.DocumentEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

private val LOG = logger<SearchableOptionsRegistrarImpl>()
private val EP_NAME = ExtensionPointName<SearchableOptionContributor>("com.intellij.search.optionContributor")
internal val WORD_SEPARATOR_CHARS: @NonNls Pattern = Pattern.compile("[^-\\pL\\d#+]+")

@ApiStatus.Internal
class SearchableOptionsRegistrarImpl : SearchableOptionsRegistrar() {
  // option => array of packed OptionDescriptor
  @Volatile
  private var storage: Map<String, LongArray> = emptyMap()

  private val stopWords: Set<String>

  @Volatile
  private var highlightOptionToSynonym = emptyMap<Pair<String, String>, MutableSet<String>>()

  private val isInitialized = AtomicBoolean()

  @Volatile
  private var identifierTable = IndexedCharsInterner()

  init {
    val app = ApplicationManager.getApplication()
    if (app.isCommandLine() || app.isHeadlessEnvironment()) {
      stopWords = emptySet()
    }
    else {
      stopWords = loadStopWords()

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
    storage = HashMap<String, LongArray>()
    isInitialized.set(false)
  }

  fun isInitialized(): Boolean {
    return isInitialized.get()
  }

  @TestOnly
  fun initializeBlocking() {
    @Suppress("SSBasedInspection")
    runBlocking(Dispatchers.Default) {
      initialize()
    }
  }

  @ApiStatus.Internal
  suspend fun initialize() {
    if (!isInitialized.compareAndSet(false, true)) {
      return
    }

    val processor = MySearchableOptionProcessor(stopWords)
    try {
      for (extension in EP_NAME.filterableLazySequence()) {
        coroutineContext.ensureActive()

        try {
          extension.instance?.contribute(processor)
        }
        catch (e: Throwable) {
          LOG.error(PluginException(e, extension.pluginDescriptor.pluginId))
        }
        catch (e: CancellationException) {
          throw e
        }
      }

      coroutineContext.ensureActive()

      highlightOptionToSynonym = processor.computeHighlightOptionToSynonym()
      storage = processor.storage
      identifierTable = processor.identifierTable
    }
    catch (e: CancellationException) {
      LOG.warn("=== Search storage init canceled ===")
      isInitialized.set(false)
      throw e
    }
  }

  /**
   * Retrieves all searchable option names.
   */
  @ApiStatus.Internal
  fun getAllOptionNames(): Set<String> = storage.keys

  @ApiStatus.Internal
  fun getStorage(): Map<String, LongArray> = java.util.Map.copyOf(storage)

  @Suppress("LocalVariableName")
  @ApiStatus.Internal
  fun unpack(data: Long): OptionDescription {
    val _groupName = (data shr 48 and 0xffffL).toInt()
    val _id = (data shr 32 and 0xffffL).toInt()
    val _hit = (data shr 16 and 0xffffL).toInt()
    val _path = (data and 0xffffL).toInt()
    assert( /*_id >= 0 && */_id < Short.Companion.MAX_VALUE)
    assert( /*_hit >= 0 && */_hit <= Short.Companion.MAX_VALUE)
    assert( /*_path >= 0 && */_path <= Short.Companion.MAX_VALUE)
    assert( /*_groupName >= 0 && */_groupName <= Short.Companion.MAX_VALUE)

    val groupName = if (_groupName == Short.MAX_VALUE.toInt()) null else identifierTable.fromId(_groupName)
    val configurableId = identifierTable.fromId(_id).toString()
    val hit = if (_hit == Short.MAX_VALUE.toInt()) null else identifierTable.fromId(_hit).toString()
    val path = if (_path == Short.MAX_VALUE.toInt()) null else identifierTable.fromId(_path).toString()

    return OptionDescription(_option = null, configurableId = configurableId, hit = hit, path = path, groupName = groupName)
  }

  override fun getConfigurables(
    groups: MutableList<out ConfigurableGroup>,
    type: DocumentEvent.EventType?,
    previouslyFiltered: MutableSet<out Configurable>?,
    option: String,
    project: Project?,
  ): ConfigurableHit {
    var previouslyFiltered = previouslyFiltered
    if (previouslyFiltered == null || previouslyFiltered.isEmpty()) {
      previouslyFiltered = null
    }

    val optionToCheck = Strings.toLowerCase(option.trim { it <= ' ' })

    val foundByPath = findGroupsByPath(groups, optionToCheck)
    if (foundByPath != null) {
      return foundByPath
    }

    val effectiveConfigurables: MutableSet<Configurable> = LinkedHashSet<Configurable>()
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
        if (displayName != null && StringUtil.containsIgnoreCase(displayName, optionToCheck)) {
          nameFullHits.add(each)
          nameHits.add(each)
        }
      }
    }

    // operate with substring
    val descriptionOptions: MutableSet<String?> = HashSet<String?>()
    if (options.isEmpty()) {
      val components = WORD_SEPARATOR_CHARS.split(optionToCheck)
      if (components.size > 0) {
        Collections.addAll<String?>(descriptionOptions, *components)
      }
      else {
        descriptionOptions.add(option)
      }
    }
    else {
      descriptionOptions.addAll(options)
    }

    val foundIds = findConfigurablesByDescriptions(descriptionOptions)
    if (foundIds == null) {
      return ConfigurableHit(nameHits = nameHits, nameFullHits = nameFullHits, contentHits = setOf<Configurable>(), spotlightFilter = option)
    }

    val contentHits = filterById(effectiveConfigurables, foundIds)

    if (type == DocumentEvent.EventType.CHANGE && previouslyFiltered != null && effectiveConfigurables.size == contentHits.size) {
      return getConfigurables(groups, DocumentEvent.EventType.CHANGE, null, option, project)
    }
    return ConfigurableHit(
      nameHits = nameHits,
      nameFullHits = nameFullHits,
      contentHits = LinkedHashSet(contentHits),
      spotlightFilter = option,
    )
  }

  private fun findConfigurablesByDescriptions(descriptionOptions: MutableSet<String?>): MutableSet<String>? {
    var helpIds: MutableSet<String>? = null
    for (prefix in descriptionOptions) {
      val optionIds = getAcceptableDescriptions(prefix)
      if (optionIds == null) {
        return null
      }

      val ids = HashSet<String>()
      for (id in optionIds) {
        ids.add(id.configurableId!!)
      }
      if (helpIds == null) {
        helpIds = ids
      }
      helpIds.retainAll(ids)
    }
    return helpIds
  }

  @Synchronized
  fun getAcceptableDescriptions(prefix: String?): MutableSet<OptionDescription>? {
    if (prefix == null) {
      return null
    }

    if (!isInitialized()) {
      LOG.warn("Not yet initialized")
    }

    val stemmedPrefix = PorterStemmerUtil.stem(prefix)
    if (stemmedPrefix == null || stemmedPrefix.isBlank()) {
      return null
    }

    var result: MutableSet<OptionDescription>? = null
    for (entry in storage.entries) {
      val descriptions = entry.value
      val option = entry.key
      if (!option.startsWith(prefix) && !option.startsWith(stemmedPrefix)) {
        val stemmedOption = PorterStemmerUtil.stem(option)
        if (stemmedOption != null && !stemmedOption.startsWith(prefix) && !stemmedOption.startsWith(stemmedPrefix)) {
          continue
        }
      }
      if (result == null) {
        result = HashSet<OptionDescription>()
      }
      for (description in descriptions) {
        result.add(unpack(description))
      }
    }
    return result
  }

  //suspend fun getAcceptableDescriptions(prefix: String): MutableSet<OptionDescription>? {
  //  val stemmedPrefix = PorterStemmerUtil.stem(prefix)
  //  if (stemmedPrefix == null || stemmedPrefix.isBlank()) {
  //    return null
  //  }
  //
  //  initialize()
  //
  //  var result: MutableSet<OptionDescription>? = null
  //  for (entry in storage.entries) {
  //    val descriptions = entry.value
  //    val option = entry.key
  //    if (!option.startsWith(prefix) && !option.startsWith(stemmedPrefix)) {
  //      val stemmedOption = PorterStemmerUtil.stem(option)
  //      if (stemmedOption != null && !stemmedOption.startsWith(prefix) && !stemmedOption.startsWith(stemmedPrefix)) {
  //        continue
  //      }
  //    }
  //    if (result == null) {
  //      result = HashSet<OptionDescription>()
  //    }
  //    for (description in descriptions) {
  //      result.add(unpack(description))
  //    }
  //  }
  //  return result
  //}

  private fun getOptionDescriptionsByWords(
    configurable: SearchableConfigurable,
    words: MutableSet<String>,
  ): MutableSet<OptionDescription>? {
    var path: MutableSet<OptionDescription>? = null

    for (word in words) {
      val configs = getAcceptableDescriptions(word)
      if (configs == null) {
        return null
      }

      val paths: MutableSet<OptionDescription> = HashSet<OptionDescription>()
      for (config in configs) {
        if (Comparing.strEqual(config.configurableId, configurable.getId())) {
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
    val result: MutableSet<String> = HashSet<String>()
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

private fun findGroupsByPath(groups: MutableList<out ConfigurableGroup>, path: String): ConfigurableHit? {
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
      current = (matched as Configurable.Composite).getConfigurables().asList()
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

  val hits = setOf(lastMatched)
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

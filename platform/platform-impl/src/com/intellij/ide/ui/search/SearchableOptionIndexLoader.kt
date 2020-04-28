// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.ResourceUtil
import com.intellij.util.text.ByteArrayCharSequence
import com.intellij.util.text.CharSequenceHashingStrategy
import gnu.trove.THashMap
import gnu.trove.THashSet
import java.net.URL

internal class MySearchableOptionProcessor(private val stopWords: Set<String>) : SearchableOptionProcessor() {
  private val cache: MutableSet<String> = HashSet()
  val storage = THashMap<CharSequence, LongArray>(20, 0.9f, CharSequenceHashingStrategy.CASE_SENSITIVE)
  val identifierTable = IndexedCharsInterner()

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
    for (word in cache) {
      putOptionWithHelpId(word, configurableId, configurableDisplayName, hit, path)
    }
  }

  fun computeHighlightOptionToSynonym(searchableOptions: MutableSet<URL>): Map<Pair<String, String>, MutableSet<String>> {
    for (url in searchableOptions) {
      val root = JDOMUtil.load(url)
      for (configurable in root.getChildren("configurable")) {
        val id = configurable.getAttributeValue("id") ?: continue
        val groupName = configurable.getAttributeValue("configurable_name")
        for (optionElement in configurable.getChildren("option")) {
          val option = optionElement.getAttributeValue("name") ?: continue
          val path = optionElement.getAttributeValue("path")
          val hit = optionElement.getAttributeValue("hit")
          putOptionWithHelpId(option, id, groupName, hit, path)
        }
      }
    }

    return loadSynonyms()
  }

  private fun loadSynonyms(): MutableMap<Pair<String, String>, MutableSet<String>> {
    val result = THashMap<Pair<String, String>, MutableSet<String>>()
    val root = JDOMUtil.load(ResourceUtil.getResourceAsStream(SearchableOptionsRegistrar::class.java, "/search/", "synonyms.xml"))
    val cache = HashSet<String>()
    for (configurable in root.getChildren("configurable")) {
      val id = configurable.getAttributeValue("id") ?: continue
      val groupName = configurable.getAttributeValue("configurable_name")
      val synonyms = configurable.getChildren("synonym")
      for (synonymElement in synonyms) {
        val synonym = synonymElement.textNormalize ?: continue
        cache.clear()
        SearchableOptionsRegistrarImpl.collectProcessedWords(synonym, cache, stopWords)
        for (word in cache) {
          putOptionWithHelpId(word, id, groupName, synonym, null)
        }
      }

      for (optionElement in configurable.getChildren("option")) {
        val option = optionElement.getAttributeValue("name")
        val list = optionElement.getChildren("synonym")
        for (synonymElement in list) {
          val synonym = synonymElement.textNormalize ?: continue
          cache.clear()
          SearchableOptionsRegistrarImpl.collectProcessedWords(synonym, cache, stopWords)
          for (word in cache) {
            putOptionWithHelpId(word, id, groupName, synonym, null)
          }
          result.getOrPut(Pair(option, id)) { THashSet() }.add(synonym)
        }
      }
    }
    return result
  }

  private fun putOptionWithHelpId(option: String, id: String, groupName: String?, hit: String?, path: String?) {
    if (stopWords.contains(option)) {
      return
    }

    val stopWord = PorterStemmerUtil.stem(option)
    if (stopWord == null || stopWords.contains(stopWord)) {
      return
    }

    var configs = storage.get(option)
    val packed = SearchableOptionsRegistrarImpl.pack(id, hit, path, groupName, identifierTable)
    if (configs == null) {
      configs = longArrayOf(packed)
    }
    else if (configs.indexOf(packed) == -1) {
      configs = ArrayUtil.append(configs, packed)
    }
    storage.put(ByteArrayCharSequence.convertToBytesIfPossible(option), configs)
  }
}
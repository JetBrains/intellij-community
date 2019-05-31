// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search

import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ResourceUtil
import gnu.trove.THashMap
import gnu.trove.THashSet
import java.net.URL

internal class SearchableOptionIndexLoader(val registrar: SearchableOptionsRegistrarImpl, storage: MutableMap<CharSequence, LongArray>) {
  // option => array of packed OptionDescriptor
  @Suppress("CanBePrimaryConstructorProperty")
  val storage: MutableMap<CharSequence, LongArray> = storage

  val highlightOptionToSynonym: MutableMap<Couple<String>, MutableSet<String>> = THashMap()

  fun load(searchableOptions: MutableSet<URL>) {
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

    loadSynonyms()
  }

  private fun loadSynonyms() {
    val root = JDOMUtil.load(ResourceUtil.getResourceAsStream(SearchableOptionsRegistrar::class.java, "/search/", "synonyms.xml"))
    for (configurable in root.getChildren("configurable")) {
      val id = configurable.getAttributeValue("id") ?: continue
      val groupName = configurable.getAttributeValue("configurable_name")
      val synonyms = configurable.getChildren("synonym")
      for (synonymElement in synonyms) {
        val synonym = synonymElement.textNormalize ?: continue
        val words = registrar.getProcessedWords(synonym)
        for (word in words) {
          putOptionWithHelpId(word, id, groupName, synonym, null)
        }
      }

      for (optionElement in configurable.getChildren("option")) {
        val option = optionElement.getAttributeValue("name")
        val list = optionElement.getChildren("synonym")
        for (synonymElement in list) {
          val synonym = synonymElement.textNormalize ?: continue
          val words = registrar.getProcessedWords(synonym)
          for (word in words) {
            putOptionWithHelpId(word, id, groupName, synonym, null)
          }
          highlightOptionToSynonym.getOrPut(Couple.of(option, id)) { THashSet() }.add(synonym)
        }
      }
    }
  }

  // ideally, loader should return immutable object, but SearchableOptionsRegistrarImpl allows runtime modification
  private fun putOptionWithHelpId(option: String, id: String, groupName: String?, hit: String?, path: String?) {
    SearchableOptionsRegistrarImpl.putOptionWithHelpId(option, id, groupName, hit, path, storage, registrar)
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jdom.Element

private object XmlTagHelper {
  const val BLACKLISTS = "blacklists"
  const val LANGUAGE_LIST = "blacklist"
  const val LANGUAGE = "language"
  const val ADDED = "added"
  const val REMOVED = "removed"
  const val PATTERN = "pattern"
  const val DISABLED_LANGUAGES = "disabledLanguages"
  const val DISABLED_LANGUAGE_ITEM = "language"
  const val DISABLED_LANGUAGE_ID = "id"
}

class Diff(val added: Set<String>, val removed: Set<String>) {
  fun applyOn(base: Set<String>): Set<String> {
    val baseSet = base.toMutableSet()
    added.forEach { baseSet.add(it) }
    removed.forEach { baseSet.remove(it) }
    return baseSet
  }

  companion object Builder {
    fun build(base: Set<String>, updated: Set<String>): Diff {
      val removed = base.toMutableSet()
      removed.removeAll(updated)

      val added = updated.toMutableSet()
      added.removeAll(base)

      return Diff(added, removed)
    }
  }
}

@State(name = "ParameterNameHintsSettings", storages = [(Storage("parameter.hints.xml"))], category = SettingsCategory.CODE)
class ParameterNameHintsSettings : PersistentStateComponent<Element> {
  private val removedPatterns = hashMapOf<String, Set<String>>()
  private val addedPatterns = hashMapOf<String, Set<String>>()
  private val options = hashMapOf<String, Boolean>()
  private val disabledLanguages = hashSetOf<String>()

  fun addIgnorePattern(language: Language, pattern: String) {
    val patternsBefore = getAddedPatterns(language)
    setAddedPatterns(language, patternsBefore + pattern)
  }

  fun getExcludeListDiff(language: Language): Diff {
    val added = getAddedPatterns(language)
    val removed = getRemovedPatterns(language)

    return Diff(added, removed)
  }

  fun setExcludeListDiff(language: Language, diff: Diff) {
    setAddedPatterns(language, diff.added)
    setRemovedPatterns(language, diff.removed)
  }

  override fun getState(): Element {
    val root = Element("settings")

    if (removedPatterns.isNotEmpty() || addedPatterns.isNotEmpty()) {
      val blacklists = Element(XmlTagHelper.BLACKLISTS)
      root.addContent(blacklists)

      val allLanguages = removedPatterns.keys + addedPatterns.keys
      allLanguages.forEach {
        val removed = removedPatterns[it] ?: emptySet()
        val added = addedPatterns[it] ?: emptySet()

        val languageBlacklist = Element(XmlTagHelper.LANGUAGE_LIST).apply {
          setAttribute(XmlTagHelper.LANGUAGE, it)
          val removedElements = removed.map { it.toPatternElement(XmlTagHelper.REMOVED) }
          val addedElements = added.map { it.toPatternElement(XmlTagHelper.ADDED) }
          addContent(addedElements + removedElements)
        }

        blacklists.addContent(languageBlacklist)
      }
    }

    options.forEach { (id, value) ->
      val element = Element("option")
      element.setAttribute("id", id)
      element.setAttribute("value", value.toString())
      root.addContent(element)
    }

    if (disabledLanguages.isNotEmpty()) {
      val disabledLanguagesElement = Element(XmlTagHelper.DISABLED_LANGUAGES)
      disabledLanguagesElement.addContent(disabledLanguages.map {
        val element = Element(XmlTagHelper.DISABLED_LANGUAGE_ITEM)
        element.setAttribute(XmlTagHelper.DISABLED_LANGUAGE_ID, it)
        element
      })
      root.addContent(disabledLanguagesElement)
    }

    return root
  }

  fun setIsEnabledForLanguage(enabled: Boolean, language: Language) {
    if (!enabled) {
      disabledLanguages.add(language.id)
    } else {
      disabledLanguages.remove(language.id)
    }
  }

  fun isEnabledForLanguage(language: Language): Boolean {
    return language.id !in disabledLanguages
  }

  override fun loadState(state: Element) {
    addedPatterns.clear()
    removedPatterns.clear()
    options.clear()
    disabledLanguages.clear()

    val allBlacklistElements = state.getChild(XmlTagHelper.BLACKLISTS)
                          ?.getChildren(XmlTagHelper.LANGUAGE_LIST) ?: emptyList()

    allBlacklistElements.forEach { blacklistElement ->
      val language = blacklistElement.attributeValue(XmlTagHelper.LANGUAGE) ?: return@forEach

      val added = blacklistElement.extractPatterns(XmlTagHelper.ADDED)
      addedPatterns[language] = addedPatterns[language]?.plus(added) ?: added

      val removed = blacklistElement.extractPatterns(XmlTagHelper.REMOVED)
      removedPatterns[language] = removedPatterns[language]?.plus(removed) ?: removed
    }

    state.getChildren("option").forEach {
      val id = it.getAttributeValue("id")
      options[id] = it.getAttributeBooleanValue("value")
    }

    state.getChild(XmlTagHelper.DISABLED_LANGUAGES)?.apply {
      getChildren(XmlTagHelper.DISABLED_LANGUAGE_ITEM).forEach {
        val languageId = it.attributeValue(XmlTagHelper.DISABLED_LANGUAGE_ID) ?: return@forEach
        disabledLanguages.add(languageId)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): ParameterNameHintsSettings = ApplicationManager.getApplication().getService(ParameterNameHintsSettings::class.java)
  }

  fun getOption(optionId: String): Boolean? {
    return options[optionId]
  }

  fun setOption(optionId: String, value: Boolean?) {
    if (value == null) {
      options.remove(optionId)
    }
    else {
      options[optionId] = value
    }
  }

  private fun getAddedPatterns(language: Language): Set<String> {
    val key = language.displayName
    return addedPatterns[key] ?: emptySet()
  }

  private fun getRemovedPatterns(language: Language): Set<String> {
    val key = language.displayName
    return removedPatterns[key] ?: emptySet()
  }

  private fun setRemovedPatterns(language: Language, removed: Set<String>) {
    val key = language.displayName
    removedPatterns[key] = removed
  }

  private fun setAddedPatterns(language: Language, added: Set<String>) {
    val key = language.displayName
    addedPatterns[key] = added
  }

}


private fun Element.extractPatterns(tag: String): Set<String> {
  return getChildren(tag).mapNotNull { it.attributeValue(XmlTagHelper.PATTERN) }.toSet()
}


private fun Element.attributeValue(attr: String): String? = this.getAttribute(attr)?.value


private fun String.toPatternElement(status: String): Element {
  val element = Element(status)
  element.setAttribute(XmlTagHelper.PATTERN, this)
  return element
}
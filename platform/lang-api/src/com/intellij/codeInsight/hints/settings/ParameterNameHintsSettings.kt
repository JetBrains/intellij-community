// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.codeInsight.hints.settings

import com.intellij.lang.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.getAttributeBooleanValue
import org.jdom.Element


private object XmlTagHelper {
  val BLACKLISTS = "blacklists"
  val LANGUAGE_LIST = "blacklist"
  val LANGUAGE = "language"
  val ADDED = "added"
  val REMOVED = "removed"
  val PATTERN = "pattern"
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


@State(name = "ParameterNameHintsSettings", storages = arrayOf(Storage("parameter.hints.xml")))
class ParameterNameHintsSettings : PersistentStateComponent<Element> {
  private val removedPatterns = hashMapOf<String, Set<String>>()
  private val addedPatterns = hashMapOf<String, Set<String>>()
  private val options = hashMapOf<String, Boolean>()
  
  fun addIgnorePattern(language: Language, pattern: String) {
    val patternsBefore = getAddedPatterns(language)
    setAddedPatterns(language, patternsBefore + pattern)
  }

  fun getBlackListDiff(language: Language): Diff {
    val added = getAddedPatterns(language)
    val removed = getRemovedPatterns(language)

    return Diff(added, removed)
  }

  fun setBlackListDiff(language: Language, diff: Diff) {
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
    
    options.forEach { id, value ->
      val element = Element("option")
      element.setAttribute("id", id)
      element.setAttribute("value", value.toString())
      root.addContent(element)
    }
    
    return root
  }

  override fun loadState(state: Element) {
    addedPatterns.clear()
    removedPatterns.clear()
    options.clear()
    
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
  }
  
  companion object {
    @JvmStatic
    fun getInstance(): ParameterNameHintsSettings = ServiceManager.getService(ParameterNameHintsSettings::class.java)
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
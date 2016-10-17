package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jdom.Element


private object XmlTagHelper {
  val BLACKLISTS = "blacklists"
  val LANGUAGE_LIST = "blacklist"
  val LANGUAGE = "language"
  val ADDED = "added"
  val REMOVED = "removed"
  val PATTERN = "pattern"
}


@State(name = "ParameterNameHintsSettings", storages = arrayOf(Storage("parameter.hints.xml")))
class ParameterNameHintsSettings : PersistentStateComponent<Element> {
  private val myRemovedPatterns = hashMapOf<String, List<String>>()
  private val myAddedPatterns = hashMapOf<String, List<String>>()

  override fun getState(): Element {
    val root = Element("settings")
    val blacklists = root.getOrCreateChild(XmlTagHelper.BLACKLISTS)

    myRemovedPatterns.forEach { language, patterns ->
      blacklists.addLanguagePatternElements(language, patterns, XmlTagHelper.REMOVED)
    }

    myAddedPatterns.forEach { language, patterns ->
      blacklists.addLanguagePatternElements(language, patterns, XmlTagHelper.ADDED)
    }

    return root
  }

  override fun loadState(state: Element) {
    val allBlackLists = state.getChild(XmlTagHelper.BLACKLISTS)?.getChildren(XmlTagHelper.LANGUAGE_LIST) ?: emptyList()
    allBlackLists.mapNotNull {
      val language = it.attributeValue(XmlTagHelper.LANGUAGE) ?: return@mapNotNull
      myAddedPatterns[language] = it.extractPatterns(XmlTagHelper.ADDED)
      myRemovedPatterns[language] = it.extractPatterns(XmlTagHelper.REMOVED)
    }
  }

  fun addIgnorePattern(language: Language, pattern: String) {
    val patternsBefore = getAddedPatterns(language)
    setAddedPatterns(language, patternsBefore + pattern)
  }

  fun getIgnorePatternSet(hintsProvider: InlayParameterHintsProvider): Set<String> {
    val added = getAddedPatterns(hintsProvider.language)
    val removed = getRemovedPatterns(hintsProvider.language)
    
    val updated = hintsProvider.defaultBlackList.toMutableSet()
    updated.removeAll(removed)
    updated.addAll(added)

    return updated
  }

  fun setIgnorePatternSet(provider: InlayParameterHintsProvider, updatedBlackList: Set<String>) {
    val defaultBlackList = provider.defaultBlackList

    val removed = defaultBlackList.toMutableSet()
    removed.removeAll(updatedBlackList)

    val added = updatedBlackList.toMutableSet()
    added.removeAll(defaultBlackList)

    val language = provider.language
    setRemovedPatterns(language, removed)
    setAddedPatterns(language, added)
  }
  
  companion object {
    @JvmStatic
    fun getInstance() = service<ParameterNameHintsSettings>()
  }

  private fun getAddedPatterns(language: Language): List<String> {
    val key = language.displayName
    return myAddedPatterns[key] ?: emptyList()
  }

  private fun getRemovedPatterns(language: Language): List<String> {
    val key = language.displayName
    return myRemovedPatterns[key] ?: emptyList()
  }

  private fun setRemovedPatterns(language: Language, removed: Collection<String>) {
    val key = language.displayName
    myRemovedPatterns[key] = removed.toList()
  }

  private fun setAddedPatterns(language: Language, added: Collection<String>) {
    val key = language.displayName
    myAddedPatterns[key] = added.toList()
  }

}

private fun Element.addLanguagePatternElements(language: String, patterns: List<String>, tag: String) {
  val list = getOrCreateChild(XmlTagHelper.LANGUAGE_LIST)
  list.setAttribute(XmlTagHelper.LANGUAGE, language)
  val elements = patterns.map { it.toPatternElement(tag) }
  list.addContent(elements)
}

private fun Element.extractPatterns(tag: String): List<String> {
  return getChildren(tag).mapNotNull { it.attributeValue(XmlTagHelper.PATTERN) }
}

private fun Element.attributeValue(attr: String): String? = this.getAttribute(attr)?.value

private fun Element.getOrCreateChild(name: String): Element {
  var child = getChild(name)
  if (child == null) {
    child = Element(name)
    addContent(child)
  }
  return child
}

private fun String.toPatternElement(status: String): Element {
  val element = Element(status)
  element.setAttribute(XmlTagHelper.PATTERN, this)
  return element
}
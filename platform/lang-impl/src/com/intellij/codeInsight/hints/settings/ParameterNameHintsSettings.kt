package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.Language
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jdom.Element


private object XmlTagHelper {
  val BLACKLIST = "blacklist"
  val LANGUAGE = "language"
  val ADDED = "added"
  val REMOVED = "removed"
  val PATTERN = "pattern"
}


@State(name = "ParameterNameHintsSettings", storages = arrayOf(Storage("parameter.hints.xml")))
class ParameterNameHintsSettings : PersistentStateComponent<Element> {
  private var myState: Element = Element("settings")
  
  override fun getState(): Element = myState

  override fun loadState(state: Element) {
    myState = state
  }
  
  private fun Language.toXmlName() = displayName.split(' ')[0]

  private fun getStoredLanguageBlackList(language: Language): Element {
    val allLists = myState.getOrCreateChild(XmlTagHelper.BLACKLIST)
    val languageTagName = language.toXmlName()
    return allLists.getOrCreateChild(languageTagName)
  }

  fun addIgnorePattern(language: Language, pattern: String) {
    val langBlackList = getStoredLanguageBlackList(language)
    val addedPattern = pattern.toPatternElement(XmlTagHelper.ADDED)
    langBlackList.addContent(addedPattern)
  }

  fun getIgnorePatternSet(hintsProvider: InlayParameterHintsProvider): Set<String> {
    val forLanguage = getStoredLanguageBlackList(hintsProvider.language).children
    val added = forLanguage.filter { it.name == XmlTagHelper.ADDED }.mapNotNull { it.getAttribute(XmlTagHelper.PATTERN)?.value }
    val removed = forLanguage.filter { it.name == XmlTagHelper.REMOVED }.mapNotNull { it.getAttribute(XmlTagHelper.PATTERN)?.value }

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

    updateState(provider.language, added, removed)
  }

  private fun updateState(language: Language, added: MutableSet<String>, removed: MutableSet<String>) {
    val languageBlackList = getStoredLanguageBlackList(language)
    
    removed.map { it.toPatternElement(XmlTagHelper.REMOVED) }
        .forEach { languageBlackList.addContent(it) }

    added.map { it.toPatternElement(XmlTagHelper.ADDED) }
        .forEach { languageBlackList.addContent(it) }
  }

  companion object {
    @JvmStatic
    fun getInstance() = service<ParameterNameHintsSettings>()
  }

}

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
package com.intellij.codeInsight.hints.settings

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
  val SHOW_IF_PARAM_NAME_CONTAINED = "showIfParamNameContained"
  val SHOW_WHEN_MULTIPLE_PARAMS_WITH_SAME_TYPE = "showWhenMultipleParamsWithSameType"
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
  private val myRemovedPatterns = hashMapOf<String, Set<String>>()
  private val myAddedPatterns = hashMapOf<String, Set<String>>()

  var isShowParamNameContainedInMethodName: Boolean = false
  var showWhenMultipleParamsWithSameType: Boolean = false

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
    val blacklists = root.getOrCreateChild(XmlTagHelper.BLACKLISTS)

    myRemovedPatterns.forEach { language, patterns ->
      blacklists.addLanguagePatternElements(language, patterns, XmlTagHelper.REMOVED)
    }

    myAddedPatterns.forEach { language, patterns ->
      blacklists.addLanguagePatternElements(language, patterns, XmlTagHelper.ADDED)
    }

    root.getOrCreateChild(XmlTagHelper.SHOW_IF_PARAM_NAME_CONTAINED)
      .setAttribute("value", isShowParamNameContainedInMethodName.toString())

    root.getOrCreateChild(XmlTagHelper.SHOW_WHEN_MULTIPLE_PARAMS_WITH_SAME_TYPE)
      .setAttribute("value", showWhenMultipleParamsWithSameType.toString())

    return root
  }

  override fun loadState(state: Element) {
    myAddedPatterns.clear()
    myRemovedPatterns.clear()

    isShowParamNameContainedInMethodName = false
    showWhenMultipleParamsWithSameType = false

    val allBlackLists = state
      .getChild(XmlTagHelper.BLACKLISTS)
      ?.getChildren(XmlTagHelper.LANGUAGE_LIST) ?: emptyList()

    allBlackLists.mapNotNull { blacklist ->
      val language = blacklist.attributeValue(XmlTagHelper.LANGUAGE) ?: return@mapNotNull
      myAddedPatterns[language] = blacklist.extractPatterns(XmlTagHelper.ADDED)
      myRemovedPatterns[language] = blacklist.extractPatterns(XmlTagHelper.REMOVED)
    }

    isShowParamNameContainedInMethodName = state.getBooleanValue(XmlTagHelper.SHOW_IF_PARAM_NAME_CONTAINED, true)
    showWhenMultipleParamsWithSameType = state.getBooleanValue(XmlTagHelper.SHOW_WHEN_MULTIPLE_PARAMS_WITH_SAME_TYPE, false)
  }

  private fun Element.getBooleanValue(childName: String, defaultValue: Boolean): Boolean {
    return getChild(childName)?.getAttributeValue("value")?.toBoolean() ?: defaultValue
  }
  
  companion object {
    @JvmStatic
    fun getInstance() = service<ParameterNameHintsSettings>()
  }

  private fun getAddedPatterns(language: Language): Set<String> {
    val key = language.displayName
    return myAddedPatterns[key] ?: emptySet()
  }

  private fun getRemovedPatterns(language: Language): Set<String> {
    val key = language.displayName
    return myRemovedPatterns[key] ?: emptySet()
  }

  private fun setRemovedPatterns(language: Language, removed: Set<String>) {
    val key = language.displayName
    myRemovedPatterns[key] = removed
  }

  private fun setAddedPatterns(language: Language, added: Set<String>) {
    val key = language.displayName
    myAddedPatterns[key] = added
  }

}

private fun Element.addLanguagePatternElements(language: String, patterns: Set<String>, tag: String) {
  val list = getOrCreateChild(XmlTagHelper.LANGUAGE_LIST)
  list.setAttribute(XmlTagHelper.LANGUAGE, language)
  val elements = patterns.map { it.toPatternElement(tag) }
  list.addContent(elements)
}

private fun Element.extractPatterns(tag: String): Set<String> {
  return getChildren(tag).mapNotNull { it.attributeValue(XmlTagHelper.PATTERN) }.toSet()
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
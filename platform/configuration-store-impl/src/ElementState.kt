// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Content
import org.jdom.Element

/** Applies changes between two serialized states to the stored XML. */
internal fun mergeElementState(stored: Element?, before: Element?, after: Element?): Element? {
  if (JDOMUtil.areElementsEqual(before, after)) {
    return stored?.clone()
  }
  if (before == null || after == null || stored == null) {
    return after?.clone()
  }

  val result = after.clone()
  val attributeNames = buildList {
    addAll(stored.attributes)
    addAll(before.attributes)
    addAll(after.attributes)
  }.map { it.name to it.namespace }.distinct()
  for ((name, namespace) in attributeNames) {
    val oldValue = before.getAttributeValue(name, namespace)
    val newValue = after.getAttributeValue(name, namespace)
    val storedValue = stored.getAttributeValue(name, namespace)
    val value = if (oldValue == newValue) storedValue else newValue
    if (value == null) {
      result.removeAttribute(name, namespace)
    }
    else {
      result.setAttribute(name, value, namespace)
    }
  }

  if (stored.children.isEmpty() && before.children.isEmpty() && after.children.isEmpty()) {
    val textSource = if (before.text == after.text) stored else after
    result.removeContent()
    result.addContent(textSource.content.map { it.clone() })
    return result
  }

  val storedGroups = stored.children.groupBy { it.name to it.namespaceURI }
  val beforeGroups = before.children.groupBy { it.name to it.namespaceURI }
  val afterGroups = after.children.groupBy { it.name to it.namespaceURI }
  val mergedGroups = LinkedHashMap<Pair<String, String>, ArrayDeque<Element>>()
  for (name in buildSet {
    addAll(storedGroups.keys)
    addAll(beforeGroups.keys)
    addAll(afterGroups.keys)
  }.distinct()) {
    val storedChildren = storedGroups[name].orEmpty()
    val beforeChildren = beforeGroups[name].orEmpty()
    val afterChildren = afterGroups[name].orEmpty()
    val identityAttribute = listOf("name", "key", "id").firstOrNull { attribute ->
      listOf(storedChildren, beforeChildren, afterChildren).all { children ->
        val values = children.map { it.getAttributeValue(attribute) }
        values.all { it != null } && values.distinct().size == values.size
      }
    }

    val merged = if (identityAttribute != null || maxOf(storedChildren.size, beforeChildren.size, afterChildren.size) <= 1) {
      fun List<Element>.byIdentity(): Map<String, Element> = associateBy { identityAttribute?.let(it::getAttributeValue) ?: "" }
      val storedById = storedChildren.byIdentity()
      val beforeById = beforeChildren.byIdentity()
      val afterById = afterChildren.byIdentity()
      val identities = afterById.keys + storedById.keys
      identities.mapNotNull { identity ->
        mergeElementState(storedById[identity], beforeById[identity], afterById[identity])
      }
    }
    else {
      // Repeated children without a unique key form one value. Keep their order and apply changes to the whole list.
      val unchanged = equalElements(beforeChildren, afterChildren)
      val children = if (unchanged) storedChildren else afterChildren
      children.map { it.clone() }
    }
    mergedGroups[name] = ArrayDeque(merged)
  }

  fun Element.nonElementContent() = content.filterNot { it is Element }.map { it.javaClass to it.value }
  val contentSource = if (before.nonElementContent() == after.nonElementContent()) stored else after
  val nonElementContent = LinkedHashMap<Int, MutableList<Content>>()
  var childIndex = 0
  for (item in contentSource.content) {
    if (item is Element) {
      childIndex++
    }
    else {
      nonElementContent.getOrPut(childIndex) { ArrayList() }.add(item)
    }
  }
  result.removeContent()
  for ((index, child) in after.children.withIndex()) {
    nonElementContent.remove(index)?.forEach { result.addContent(it.clone()) }
    mergedGroups[child.name to child.namespaceURI]?.removeFirstOrNull()?.let { result.addContent(it) }
  }
  nonElementContent.values.flatten().forEach { result.addContent(it.clone()) }
  for (children in mergedGroups.values) {
    children.forEach { result.addContent(it) }
  }
  return result
}

private fun equalElements(first: List<Element>, second: List<Element>): Boolean =
  first.size == second.size && first.indices.all { JDOMUtil.areElementsEqual(first[it], second[it]) }

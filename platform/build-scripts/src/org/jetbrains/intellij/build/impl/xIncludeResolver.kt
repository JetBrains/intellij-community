// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jdom.Namespace

private fun isIncludeElement(element: Element): Boolean {
  return element.name == "include" && element.namespace == JDOMUtil.XINCLUDE_NAMESPACE
}

private fun extractNeededChildren(element: Element, remoteElement: Element): MutableList<Element> {
  val xpointer = element.getAttributeValue("xpointer") ?: "xpointer(/idea-plugin/*)"

  var matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer)
  if (!matcher.matches()) {
    throw RuntimeException("Unsupported XPointer: $xpointer")
  }

  val pointer = matcher.group(1)
  matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer)
  if (!matcher.matches()) {
    throw RuntimeException("Unsupported pointer: $pointer")
  }

  val rootTagName = matcher.group(1)

  var e = remoteElement
  if (e.name != rootTagName) {
    return mutableListOf()
  }

  val subTagName = matcher.group(2)
  if (subTagName != null) {
    // cut off the slash
    e = requireNotNull(e.getChild(subTagName.substring(1))) { "Child element not found: ${subTagName.substring(1)}" }
  }
  return e.children.toMutableList()
}

/**
 * Resolver that returns JDOM Elements directly instead of file paths.
 * Useful when working with preloaded/cached XML documents.
 */
interface XIncludeElementResolver {
  // Return the Element for the given href, or null if not found/optional
  fun resolveElement(relativePath: String, isOptional: Boolean, isDynamic: Boolean): Element?
}

/**
 * Recursively resolves XInclude elements using preloaded Elements instead of file paths.
 *
 * This is an optimized variant useful when working with cached or scrambled descriptors
 * already loaded in memory, avoiding file I/O operations.
 *
 * @param element The root element to process. Will be mutated in place.
 * @param elementResolver Strategy for resolving include references to preloaded Elements.
 * @see resolveIncludes The file-based variant
 */
internal fun resolveIncludes(element: Element, elementResolver: XIncludeElementResolver) {
  check(!isIncludeElement(element))
  doResolveNonXIncludeElementFromCache(original = element, elementResolver = elementResolver)
}

private fun resolveXIncludeElement(element: Element, elementResolver: XIncludeElementResolver): MutableList<Element>? {
  val href = requireNotNull(element.getAttributeValue("href")) { "Missing href attribute" }

  val baseAttribute = element.getAttributeValue("base", Namespace.XML_NAMESPACE)
  if (baseAttribute != null) {
    throw UnsupportedOperationException("`base` attribute is not supported")
  }

  val fallbackElement = element.getChild("fallback", element.namespace)
  val isDynamic = element.getAttribute("includeUnless") != null || element.getAttribute("includeIf") != null
  val remoteElement = elementResolver.resolveElement(
    relativePath = href,
    isOptional = fallbackElement != null,
    isDynamic = isDynamic,
  ) ?: return null

  val remoteParsed = extractNeededChildren(element, remoteElement)

  // Process all children, recursively resolving any nested xi:include elements
  var i = 0
  while (i < remoteParsed.size) {
    val child = remoteParsed.get(i)
    if (isIncludeElement(child)) {
      val elements = resolveXIncludeElement(element = child, elementResolver = elementResolver)
      if (elements != null) {
        if (elements.isEmpty()) {
          // Remove the xi:include element that resolves to nothing
          remoteParsed.removeAt(i)
          i--  // Adjust index since we removed an element
        }
        else {
          // Replace the xi:include element with resolved elements
          remoteParsed.removeAt(i)
          remoteParsed.addAll(i, elements)
          // Skip over the newly inserted elements (loop will increment i by 1)
          i += elements.size - 1
        }
      }
    }
    else {
      doResolveNonXIncludeElementFromCache(original = child, elementResolver = elementResolver)
    }

    i++
  }

  for (element in remoteParsed) {
    element.detach()
  }
  return remoteParsed
}

private fun doResolveNonXIncludeElementFromCache(original: Element, elementResolver: XIncludeElementResolver) {
  val contentList = original.content
  for (i in contentList.size - 1 downTo 0) {
    val content = contentList.get(i)
    if (content is Element) {
      if (isIncludeElement(content)) {
        val result = resolveXIncludeElement(element = content, elementResolver = elementResolver)
        if (result != null) {
          original.setContent(i, result)
        }
      }
      else {
        // process child element to resolve possible includes
        doResolveNonXIncludeElementFromCache(original = content, elementResolver = elementResolver)
      }
    }
  }
}
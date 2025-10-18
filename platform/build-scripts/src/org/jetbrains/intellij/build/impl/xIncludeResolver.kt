// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import io.opentelemetry.api.trace.Span
import org.jdom.Element
import org.jdom.Namespace
import java.io.IOException
import java.nio.file.Path
import java.util.*

/**
 * The original element will be mutated in place.
 */
internal fun resolveNonXIncludeElement(original: Element, base: Path, pathResolver: XIncludePathResolver, trackSourceFile: Boolean = false) {
  check(!isIncludeElement(original))
  val bases = ArrayDeque<Path>()
  bases.push(base)
  doResolveNonXIncludeElement(original = original, bases = bases, pathResolver = pathResolver, trackSourceFile = trackSourceFile)
}

private fun isIncludeElement(element: Element): Boolean {
  return element.name == "include" && element.namespace == JDOMUtil.XINCLUDE_NAMESPACE
}

internal const val SOURCE_FILE_ATTRIBUTE = "source-file"
internal const val MODULE_SET_CHAIN_SEPARATOR = " <- "

private fun resolveXIncludeElement(element: Element, bases: Deque<Path>, pathResolver: XIncludePathResolver, trackSourceFile: Boolean): MutableList<Element>? {
  val base = bases.peek()
  val href = requireNotNull(element.getAttributeValue("href")) { "Missing href attribute" }

  val baseAttribute = element.getAttributeValue("base", Namespace.XML_NAMESPACE)
  if (baseAttribute != null) {
    throw UnsupportedOperationException("`base` attribute is not supported")
  }

  val fallbackElement = element.getChild("fallback", element.namespace)
  val remote = pathResolver.resolvePath(
    relativePath = href,
    base = base,
    isOptional = fallbackElement != null,
    isDynamic = element.getAttribute("includeUnless") != null || element.getAttribute("includeIf") != null,
  ) ?: return null

  assert(!bases.contains(remote)) { "Circular XInclude Reference to $remote" }

  val remoteElement = parseRemote(bases = bases, remote = remote, fallbackElement = fallbackElement, pathResolver = pathResolver, trackSourceFile = trackSourceFile) ?: return null
  val remoteParsed = extractNeededChildren(element, remoteElement)

  // Add source-file attribute to <content> elements if tracking is enabled
  // Build chain incrementally by prepending to existing chains to preserve include hierarchy
  // Strip .xml extension here so downstream code doesn't need to
  // Only add module set files to chain (skip product files)
  if (trackSourceFile) {
    for (resolvedElement in remoteParsed) {
      if (resolvedElement.name == "content") {
        val existingChain = resolvedElement.getAttributeValue(SOURCE_FILE_ATTRIBUTE)
        val currentName = remote.fileName.toString().removeSuffix(".xml")

        // Only add to chain if it's a module set file
        if (currentName.startsWith("intellij.moduleSets.")) {
          val newChain = if (existingChain != null) {
            "$currentName$MODULE_SET_CHAIN_SEPARATOR$existingChain"
          }
          else {
            currentName
          }
          resolvedElement.setAttribute(SOURCE_FILE_ATTRIBUTE, newChain)
        }
      }
    }
  }

  var i = 0
  while (true) {
    if (i >= remoteParsed.size) {
      break
    }

    val o = remoteParsed.get(i)
    if (isIncludeElement(o)) {
      val elements = resolveXIncludeElement(element = o, bases = bases, pathResolver = pathResolver, trackSourceFile = trackSourceFile)
      if (elements != null) {
        remoteParsed.addAll(i, elements)
        i += elements.size - 1
        remoteParsed.removeAt(i)
      }
    }
    else {
      doResolveNonXIncludeElement(original = o, bases = bases, pathResolver = pathResolver, trackSourceFile = trackSourceFile)
    }

    i++
  }

  for (element in remoteParsed) {
    element.detach()
  }
  return remoteParsed
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
    e = e.getChild(subTagName.substring(1))
    assert(e != null)
  }
  return e.children.toMutableList()
}

private fun parseRemote(bases: Deque<Path>, remote: Path, fallbackElement: Element?, pathResolver: XIncludePathResolver, trackSourceFile: Boolean): Element? {
  try {
    bases.push(remote)
    val root = JDOMUtil.load(remote)
    if (isIncludeElement(root)) {
      val resolved = resolveXIncludeElement(element = root, bases = bases, pathResolver = pathResolver, trackSourceFile = trackSourceFile)
      return if (resolved.isNullOrEmpty()) root else resolved.single()
    }
    else {
      doResolveNonXIncludeElement(original = root, bases = bases, pathResolver = pathResolver, trackSourceFile = trackSourceFile)
      return root
    }
  }
  catch (e: IOException) {
    if (fallbackElement != null) {
      return null
    }
    Span.current().addEvent("$remote include ignored: ${e.message}")
    return null
  }
  finally {
    bases.pop()
  }
}

private fun doResolveNonXIncludeElement(original: Element, bases: Deque<Path>, pathResolver: XIncludePathResolver, trackSourceFile: Boolean) {
  val contentList = original.content
  for (i in contentList.size - 1 downTo 0) {
    val content = contentList.get(i)
    if (content is Element) {
      if (isIncludeElement(content)) {
        val result = resolveXIncludeElement(element = content, bases = bases, pathResolver = pathResolver, trackSourceFile = trackSourceFile)
        if (result != null) {
          original.setContent(i, result)
        }
      }
      else {
        // process child element to resolve possible includes
        doResolveNonXIncludeElement(original = content, bases = bases, pathResolver = pathResolver, trackSourceFile = trackSourceFile)
      }
    }
  }
}

interface XIncludePathResolver {
  // return null if there is no need to resolve x-include
  fun resolvePath(relativePath: String, base: Path?, isOptional: Boolean, isDynamic: Boolean): Path?
}
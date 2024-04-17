// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import io.opentelemetry.api.trace.Span
import org.jdom.Element
import org.jdom.Namespace
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import java.util.*

/**
 * The original element will be mutated in place.
 */
internal fun resolveNonXIncludeElement(original: Element, base: Path, pathResolver: XIncludePathResolver) {
  check(!isIncludeElement(original))
  val bases = ArrayDeque<URL>()
  bases.push(base.toUri().toURL())
  doResolveNonXIncludeElement(original = original, bases = bases, pathResolver = pathResolver)
}

private fun isIncludeElement(element: Element): Boolean {
  return element.name == "include" && element.namespace == JDOMUtil.XINCLUDE_NAMESPACE
}

private fun resolveXIncludeElement(element: Element, bases: Deque<URL>, pathResolver: XIncludePathResolver): MutableList<Element> {
  var base: URL? = null
  if (!bases.isEmpty()) {
    base = bases.peek()
  }

  val href = element.getAttributeValue("href")
  assert(href != null) { "Missing href attribute" }

  val baseAttribute = element.getAttributeValue("base", Namespace.XML_NAMESPACE)
  if (baseAttribute != null) {
    base = URL(baseAttribute)
  }

  val remote = pathResolver.resolvePath(href, base)
  assert(!bases.contains(remote)) { "Circular XInclude Reference to ${remote.toExternalForm()}" }

  val fallbackElement = element.getChild("fallback", element.namespace)
  var remoteParsed = parseRemote(bases, remote, fallbackElement, pathResolver)
  if (!remoteParsed.isEmpty()) {
    remoteParsed = extractNeededChildren(element, remoteParsed)
  }

  var i = 0
  while (true) {
    if (i >= remoteParsed.size) {
      break
    }

    val o = remoteParsed.get(i)
    if (isIncludeElement(o)) {
      val elements = resolveXIncludeElement(o, bases, pathResolver)
      remoteParsed.addAll(i, elements)
      i += elements.size - 1
      remoteParsed.removeAt(i)
    }
    else {
      doResolveNonXIncludeElement(o, bases, pathResolver)
    }

    i++
  }

  remoteParsed.forEach(Element::detach)
  return remoteParsed
}

private fun extractNeededChildren(element: Element, remoteElements: List<Element>): MutableList<Element> {
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

  assert(remoteElements.size == 1)
  var e = remoteElements.get(0)
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

private fun parseRemote(
  bases: Deque<URL>,
  remote: URL,
  fallbackElement: Element?,
  pathResolver: XIncludePathResolver
): MutableList<Element> {
  try {
    bases.push(remote)
    val root = JDOMUtil.load(remote.openStream())
    return if (isIncludeElement(root)) {
      resolveXIncludeElement(root, bases, pathResolver)
    }
    else {
      doResolveNonXIncludeElement(root, bases, pathResolver)
      mutableListOf(root)
    }
  }
  catch (e: IOException) {
    if (fallbackElement != null) {
      return mutableListOf()
    }
    Span.current().addEvent("${remote.toExternalForm()} include ignored: ${e.message}")
    return mutableListOf()
  }
  finally {
    bases.pop()
  }
}

private fun doResolveNonXIncludeElement(original: Element, bases: Deque<URL>, pathResolver: XIncludePathResolver) {
  val contentList = original.content
  for (i in contentList.size - 1 downTo 0) {
    val content = contentList.get(i)
    if (content is Element) {
      if (isIncludeElement(content)) {
        original.setContent(i, resolveXIncludeElement(content, bases, pathResolver))
      }
      else {
        // process child element to resolve possible includes
        doResolveNonXIncludeElement(content, bases, pathResolver)
      }
    }
  }
}

interface XIncludePathResolver {
  fun resolvePath(relativePath: String, base: URL?): URL
}
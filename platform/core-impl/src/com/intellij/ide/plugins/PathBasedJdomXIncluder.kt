// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SafeJdomFactory
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.Namespace
import java.io.IOException

interface PathResolver {
  val isFlat: Boolean
    get() = false

  @Throws(IOException::class, JDOMException::class)
  fun loadXIncludeReference(dataLoader: DataLoader,
                            base: String?,
                            relativePath: String,
                            jdomFactory: SafeJdomFactory): Element?

  @Throws(IOException::class, JDOMException::class)
  fun resolvePath(dataLoader: DataLoader, relativePath: String, jdomFactory: SafeJdomFactory): Element?
}

internal class PathBasedJdomXIncluder(private val context: DescriptorListLoadingContext, private val pathResolver: PathResolver) {
  @Throws(JDOMException::class)
  private fun resolveXIncludeElement(dataLoader: DataLoader,
                                     linkElement: Element,
                                     base: String?,
                                     result: ArrayList<Element>?): List<Element?> {
    val relativePath = linkElement.getAttributeValue(HREF) ?: throw RuntimeException("Missing href attribute")
    linkElement.getAttributeValue(PARSE)?.let {
      LOG.assertTrue(it == XML, "$it is not a legal value for the parse attribute")
    }

    var remoteParsed = loadXIncludeReference(dataLoader, base, relativePath, linkElement)
    if (remoteParsed != null) {
      val xpointer = linkElement.getAttributeValue(XPOINTER)
      if (xpointer != null) {
        remoteParsed = extractNeededChildren(remoteParsed, xpointer)
      }
    }
    if (remoteParsed == null) {
      return result ?: emptyList()
    }

    var newResult = result
    if (newResult == null) {
      newResult = ArrayList(remoteParsed.contentSize)
    }
    else {
      newResult.ensureCapacity(newResult.size + remoteParsed.contentSize)
    }

    val childBase = getChildBase(base, relativePath)
    val iterator = remoteParsed.content.iterator()
    while (iterator.hasNext()) {
      val content = iterator.next() as? Element ?: continue
      iterator.remove()
      if (isIncludeElement(content)) {
        resolveXIncludeElement(dataLoader, content, childBase, newResult)
      }
      else {
        resolveNonXIncludeElement(dataLoader, content, childBase)
        newResult.add(content)
      }
    }
    return newResult
  }

  @Throws(JDOMException::class)
  private fun loadXIncludeReference(dataLoader: DataLoader,
                                    base: String?,
                                    relativePath: String,
                                    referrerElement: Element): Element? {
    var root: Element?
    var readError: IOException? = null
    try {
      referrerElement.getAttributeValue(BASE, Namespace.XML_NAMESPACE)?.let {
        // to simplify implementation, no need to support obscure and not used base attribute
        LOG.error("Do not use xml:base attribute: $it")
      }
      root = pathResolver.loadXIncludeReference(dataLoader, base, relativePath, context.xmlFactory)
    }
    catch (e: IOException) {
      readError = e
      root = null
    }

    if (root == null) {
      referrerElement.getChild("fallback", referrerElement.namespace)?.let {
        // we don't have fallback elements with content ATM
        return null
      }

      if (context.ignoreMissingInclude) {
        LOG.info("$relativePath include ignored (dataLoader=$dataLoader)", readError)
        return null
      }
      else {
        throw RuntimeException("Cannot resolve $relativePath (dataLoader=$dataLoader)", readError)
      }
    }
    else if (isIncludeElement(root)) {
      throw UnsupportedOperationException("root tag of remote cannot be include")
    }
    else {
      resolveNonXIncludeElement(dataLoader, root, getChildBase(base, relativePath))
      return root
    }
  }

  @Throws(JDOMException::class)
  fun resolveNonXIncludeElement(dataLoader: DataLoader, original: Element, base: String?) {
    val contentList = original.content
    for (i in contentList.indices.reversed()) {
      val content = contentList[i] as? Element ?: continue
      if (isIncludeElement(content)) {
        original.setContent(i, resolveXIncludeElement(dataLoader, content, base, null))
      }
      else {
        // process child element to resolve possible includes
        resolveNonXIncludeElement(dataLoader, content, base)
      }
    }
  }
}

private const val INCLUDE = "include"
private const val HREF = "href"
private const val BASE = "base"
private const val PARSE = "parse"
private const val XML = "xml"
private const val XPOINTER = "xpointer"

private val LOG = Logger.getInstance(PathBasedJdomXIncluder::class.java)

private fun isIncludeElement(element: Element): Boolean {
  return element.name == INCLUDE && element.namespace == JDOMUtil.XINCLUDE_NAMESPACE
}

private fun getChildBase(base: String?, relativePath: String): String? {
  val end = relativePath.lastIndexOf('/')
  if (end <= 0 || relativePath.startsWith("/META-INF/")) {
    return base
  }

  val childBase = relativePath.substring(0, end)
  return if (base == null) childBase else "$base/$childBase"
}

private fun extractNeededChildren(remoteElement: Element, xpointer: String): Element? {
  var matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer)
  if (!matcher.matches()) {
    throw RuntimeException("Unsupported XPointer: $xpointer")
  }

  val pointer = matcher.group(1)
  matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer)
  if (!matcher.matches()) {
    throw RuntimeException("Unsupported pointer: $pointer")
  }

  if (remoteElement.name != matcher.group(1)) {
    return null
  }

  val subTagName = matcher.group(2) ?: return remoteElement
  // cut off the slash
  return remoteElement.getChild(subTagName.substring(1))!!
}
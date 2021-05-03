// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

private val EMPTY_ARRAY = emptyArray<XmlElement>()

@ApiStatus.Internal
data class XmlElement(
  @JvmField val name: String,
  @JvmField val attributes: Map<String, String>,

  @JvmField var children: List<XmlElement>,

  @JvmField var content: String?,
) {
  fun count(name: String): Int = children.count { it.name == name }
}

fun readXmlAsModel(reader: XMLStreamReader2): XmlElement {
  val fragment = XmlElement(name = reader.localName, attributes = readAttributes(reader = reader), children = Collections.emptyList(), content = null)
  var current = fragment
  var currentChildren = ArrayList<XmlElement>()
  val stack = ArrayDeque<XmlElement>()
  val currentChildrenStack = ArrayDeque<ArrayList<XmlElement>>()
  val listPool = ArrayDeque<ArrayList<XmlElement>>()
  var depth = 1
  while (depth > 0 && reader.hasNext()) {
    when (reader.next()) {
      XMLStreamConstants.START_ELEMENT -> {
        val child = XmlElement(reader.localName, attributes = readAttributes(reader), children = Collections.emptyList(), content = null)
        currentChildren.add(child)

        if (reader.isEmptyElement) {
          reader.skipElement()
          continue
        }

        currentChildrenStack.add(currentChildren)
        stack.addLast(current)

        currentChildren = listPool.pollLast() ?: ArrayList<XmlElement>()
        current = child
        depth++
      }
      XMLStreamConstants.END_ELEMENT -> {
        if (!currentChildren.isEmpty()) {
          @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
          current.children = Arrays.asList(*currentChildren.toArray(EMPTY_ARRAY))
        }

        currentChildren.clear()
        listPool.addLast(currentChildren)

        depth--
        if (depth == 0) {
          return fragment
        }

        currentChildren = currentChildrenStack.removeLast()
        current = stack.removeLast()
      }
      XMLStreamConstants.CDATA -> {
        if (current.content == null) {
          current.content = reader.text
        }
        else {
          current.content += reader.text
        }
      }
      XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) {
          if (current.content == null) {
            current.content = reader.text
          }
          else {
            current.content += reader.text
          }
        }
      }
      XMLStreamConstants.SPACE, XMLStreamConstants.ENTITY_REFERENCE, XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION -> {
      }
      else -> throw XMLStreamException("Unexpected XMLStream event ${reader.eventType}", reader.location)
    }
  }
  return fragment
}

private fun readAttributes(reader: XMLStreamReader2): Map<String, String> {
  return when (val attributeCount = reader.attributeCount) {
    0 -> {
      Collections.emptyMap()
    }
    1 -> {
      Collections.singletonMap(reader.getAttributeLocalName(0), reader.getAttributeValue(0))
    }
    else -> {
      // Map.of cannot be used here - in core-impl only Java 8 is allowed
      @Suppress("SSBasedInspection")
      val result = Object2ObjectOpenHashMap<String, String>(attributeCount)
      var i = 0
      while (i < attributeCount) {
        result.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i))
        i++
      }
      result
    }
  }
}
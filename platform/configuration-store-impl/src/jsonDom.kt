// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.serialization.json.*
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

@Internal
@VisibleForTesting
fun jsonDomToXml(jsonObject: JsonObject): Element {
  val element = Element(true, jsonObject.get("name")!!.jsonPrimitive.content, Namespace.NO_NAMESPACE)
  val content = jsonObject.get("content")?.jsonPrimitive?.content

  val attributes = jsonObject.get("attributes")?.jsonObject
  if (!attributes.isNullOrEmpty()) {
    for ((name, value) in attributes) {
      element.setAttribute(Attribute(true, name, value.jsonPrimitive.content, Namespace.NO_NAMESPACE))
    }
  }

  if (content == null) {
    val children = jsonObject.get("children")?.jsonArray
    if (!children.isNullOrEmpty()) {
      for (child in children) {
        element.addContent(jsonDomToXml(child.jsonObject))
      }
    }
  }
  else {
    element.addContent(Text(true, content))
  }
  return element
}

internal fun jdomToJson(element: Element): JsonElement {
  // todo optimize
  val xmlOutputter = JbXmlOutputter()
  val byteOut = BufferExposingByteArrayOutputStream()
  byteOut.writer().use {
    xmlOutputter.output(element, it)
  }

  return Json.encodeToJsonElement(readXmlAsModel(byteOut.toByteArray()))
}


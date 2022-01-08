// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization.xml

import com.intellij.util.XmlElement
import com.intellij.util.xmlb.NotNullDeserializeBinding
import com.intellij.util.xmlb.SerializationFilter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jdom.CDATA
import org.jdom.Element

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
  ignoreUnknownKeys = true
}

internal class KotlinxSerializationBinding(private val serializer: KSerializer<Any>) : NotNullDeserializeBinding() {
  override fun serialize(o: Any, context: Any?, filter: SerializationFilter?): Any {
    val element = Element("state")
    element.addContent(CDATA(json.encodeToString(serializer, o)))
    return element
  }

  override fun isBoundTo(element: Element): Boolean {
    throw UnsupportedOperationException("Only root object is supported")
  }

  override fun isBoundTo(element: XmlElement): Boolean {
    throw UnsupportedOperationException("Only root object is supported")
  }

  override fun deserialize(context: Any?, element: Element): Any {
    val cdata = element.content.firstOrNull() as? CDATA
    if (cdata == null) {
      LOG.debug("incorrect data (old format?) for $serializer")
      return json.decodeFromString(serializer, "{}")
    }
    return json.decodeFromString(serializer, cdata.text)
  }

  override fun deserialize(context: Any?, element: XmlElement): Any {
    throw UnsupportedOperationException("Only JDOM is supported for now")
  }
}
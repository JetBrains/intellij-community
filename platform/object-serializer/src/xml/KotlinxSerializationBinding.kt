// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization.xml

import com.intellij.openapi.diagnostic.debug
import com.intellij.serialization.LOG
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xmlb.Binding
import com.intellij.util.xmlb.DomAdapter
import com.intellij.util.xmlb.RootBinding
import com.intellij.util.xmlb.SerializationFilter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.jdom.CDATA
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
  ignoreUnknownKeys = true
}

private val lookup = MethodHandles.lookup()
private val kotlinMethodType = MethodType.methodType(KSerializer::class.java)

@Internal
class KotlinxSerializationBinding(aClass: Class<*>) : Binding, RootBinding {
  @JvmField
  val serializer: KSerializer<Any>

  init {
    val findStaticGetter = lookup.findStaticGetter(aClass, "Companion", aClass.classLoader.loadClass(aClass.name + "\$Companion"))
    val companion = findStaticGetter.invoke()
    @Suppress("UNCHECKED_CAST")
    serializer = lookup.findVirtual(companion.javaClass, "serializer", kotlinMethodType).invoke(companion) as KSerializer<Any>
  }

  override fun toJson(bean: Any, filter: SerializationFilter?): JsonElement {
    return json.encodeToJsonElement(serializer, bean)
  }

  override fun fromJson(currentValue: Any?, element: JsonElement): Any? {
    return if (element == JsonNull) null else json.decodeFromJsonElement(serializer, element)
  }

  override fun serialize(bean: Any, parent: Element, filter: SerializationFilter?) {
    val json = encodeToJson(bean)
    if (!json.isEmpty() && json != "{\n}") {
      parent.addContent(CDATA(json))
    }
  }

  override fun serialize(bean: Any, filter: SerializationFilter?): Element {
    val element = Element("state")
    val json = encodeToJson(bean)
    if (!json.isEmpty() && json != "{\n}") {
      element.addContent(CDATA(json))
    }
    return element
  }

  private fun encodeToJson(o: Any): String = json.encodeToString(serializer, o)

  private fun decodeFromJson(data: String): Any = json.decodeFromString(serializer, data)

  override fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean {
    throw UnsupportedOperationException("Only root object is supported")
  }

  override fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any {
    val cdata = if (element is Element) (element.content.firstOrNull() as? Text)?.text else (element as XmlElement).content
    if (cdata == null) {
      LOG.debug { "incorrect data (old format?) for $serializer" }
      return json.decodeFromString(serializer, "{}")
    }
    return decodeFromJson(cdata)
  }
}
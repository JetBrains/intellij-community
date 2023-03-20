// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.util.xml.dom.XmlElement

data class ZenDeskField(val id: Long, val type: String?, val value: String?) {
  companion object {
    fun parse(element: XmlElement): ZenDeskField {
      val id = element.getAttributeValue("id")!!.toLong()
      val type = element.getAttributeValue("type")
      val value = element.getAttributeValue("value")
      return ZenDeskField(id, type, value)
    }
  }
}

class ZenDeskForm(val id: Long, val url: String, val fields: List<ZenDeskField>) {
  companion object {
    @JvmStatic fun parse(element: XmlElement): ZenDeskForm {
      val id = element.getAttributeValue("zendesk-form-id")!!.toLong()
      val url = element.getAttributeValue("zendesk-url")!!
      val fields = mutableListOf<ZenDeskField>()

      for (child in element.children) {
        if (child.name == "field") {
          fields.add(ZenDeskField.parse(child))
        }
      }
      return ZenDeskForm(id, url, fields)
    }
  }
}

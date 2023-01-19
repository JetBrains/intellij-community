// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import org.jdom.Element

object ProfileMigrationUtils {
  fun readXmlOptions(element: Element): Map<String, String> {
    return element.children.filter { it.name == "option" }.associate { node ->
      val name = node.getAttributeValue("name")
      val value = node.getAttributeValue("value")
      name to value
    }
  }

  fun writeXmlOptions(element: Element, options: Map<String, *>) {
    options.forEach { (key, value) ->
      val child = Element("option")
      element.addContent(child)
      if (value is String) {
        child.setAttribute("name", key)
        child.setAttribute("value", value)
      }
    }
  }
}
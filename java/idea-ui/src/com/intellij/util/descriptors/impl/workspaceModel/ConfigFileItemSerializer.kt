// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.descriptors.impl.workspaceModel

import org.jdom.Element
import org.jetbrains.annotations.NonNls

interface ConfigFileItemSerializer {
  fun serializeConfigFiles(configFiles: List<ConfigFileItem>, rootElement: Element) {
    val descriptors = Element(DESCRIPTORS_ELEMENT)
    for (configFile in configFiles) {
      val child = Element(ELEMENT_NAME)
      child.setAttribute(ID_ATTRIBUTE, configFile.id)
      child.setAttribute(URL_ATTRIBUTE, configFile.url)
      descriptors.addContent(child)
    }
    if (descriptors.contentSize != 0) {
      rootElement.addContent(descriptors)
    }
  }

  fun deserializeConfigFiles(rootElement: Element): MutableList<ConfigFileItem> {
    val configFiles = mutableListOf<ConfigFileItem>()
    val descriptorsElement = rootElement.getChild(DESCRIPTORS_ELEMENT)
    if (descriptorsElement != null) {
      val children: List<Element> = descriptorsElement.getChildren(ELEMENT_NAME)
      for (child in children) {
        val id = child.getAttributeValue(ID_ATTRIBUTE)
        if (id != null) {
          val url = child.getAttributeValue(URL_ATTRIBUTE)
          if (null != url) {
            configFiles.add(ConfigFileItem(id, url))
          }
        }
      }
    }
    return configFiles
  }

  companion object {
    @NonNls
    val DESCRIPTORS_ELEMENT = "descriptors"

    @NonNls
    val ELEMENT_NAME = "deploymentDescriptor"

    @NonNls
    val ID_ATTRIBUTE = "name"

    @NonNls
    val URL_ATTRIBUTE = "url"
  }
}
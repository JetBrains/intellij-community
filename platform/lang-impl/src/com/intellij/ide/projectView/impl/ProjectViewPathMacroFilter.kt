// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.application.PathMacroFilter
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Parent

internal class ProjectViewPathMacroFilter : PathMacroFilter() {
  override fun skipPathMacros(attribute: Attribute): Boolean {
    val name: String = attribute.name
    val element: Element? = attribute.parent
    val parent: Parent? = element?.parent
    var component: Parent? = parent?.parent
    while (component != null) {
      if (component is Element && component.name == "component") {
        break
      }
      component = component.parent
    }
    return name == "name" &&
           element?.name == "item" &&
           parent is Element && parent.name == "path" &&
           component is Element && component.getAttributeValue("name") == "ProjectView"
  }
}

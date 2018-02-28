// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.noria

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import java.awt.FlowLayout
import java.awt.LayoutManager

interface BaseProps {
  var constraints: Any
}

data class BasePropsData(override var constraints: Any = "") : BaseProps

data class Checkbox(val text: String,
                    val selected: Boolean,
                    val focusable: Boolean = false,
                    val enabled: Boolean = true,
                    val onChange: (Boolean) -> Unit) : BaseProps by BasePropsData()

val CHECKBOX_COMPONENT_TYPE = "checkbox"
val checkbox = primitiveComponent<Checkbox>(CHECKBOX_COMPONENT_TYPE)

data class Button(val text: String = "",
                  val onClick: () -> Unit) : BaseProps by BasePropsData()

val BUTTON_COMPONENT_TYPE = "button"
val button = primitiveComponent<Button>(BUTTON_COMPONENT_TYPE)

data class Label(var text: String = "") : BaseProps by BasePropsData()

val LABEL_COMPONENT_TYPE = "label"
val label = primitiveComponent<Label>(LABEL_COMPONENT_TYPE)

data class Panel(val layout: LayoutManager = FlowLayout()) : BaseProps by BasePropsData()

val PANEL_COMPONENT_TYPE = "panel"
val panel = primitiveComponent<Panel>(PANEL_COMPONENT_TYPE)


interface PrimitiveComponentType<C : Any, in T : BaseProps> {
  companion object {
    val EP_NAME: ExtensionPointName<PrimitiveComponentType<*, *>> = ExtensionPointName.create<PrimitiveComponentType<*, *>>(
        "com.intellij.openapi.ui.noria.BasicUIComponentTypeEP")

    private fun getTypes() = Extensions.getExtensions(EP_NAME)
    fun getComponents(): Map<String, PrimitiveComponentType<*, *>> = getTypes().map { it.type to it }.toMap()
  }

  val type: String
  fun createNode(e: T): C
  fun update(info: UpdateInfo<C, T>)
  fun disposeNode(node: C) {
  }
}

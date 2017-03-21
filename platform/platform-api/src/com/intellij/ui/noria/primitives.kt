/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

val checkbox = primitiveComponent<Checkbox>("checkbox")

data class Button(val text: String = "",
                  val onClick: () -> Unit) : BaseProps by BasePropsData()

val button = primitiveComponent<Button>("button")

data class Label(var text: String = "") : BaseProps by BasePropsData()

val label = primitiveComponent<Label>("label")

data class Panel(val layout: LayoutManager = FlowLayout()) : BaseProps by BasePropsData()

val panel = primitiveComponent<Panel>("panel")


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

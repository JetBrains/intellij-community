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
import com.intellij.ui.NonFocusableCheckBox
import java.awt.event.ActionListener
import javax.swing.*
import kotlin.reflect.KProperty1

data class UpdateInfo<out C : JComponent, out T : Any>(val c: C, val old: T, val new: T)

fun <C : JComponent, T : Any, R> UpdateInfo<C, T>.updateProp(p: KProperty1<T, R>, setter: C.(R) -> Unit) {
  if (p.get(old) != p.get(new)) {
    c.setter(p.get(new))
  }
}

interface BasicUIComponentType<C : JComponent, in T : BaseProps> {
  companion object {
    val EP_NAME: ExtensionPointName<BasicUIComponentType<*, *>> = ExtensionPointName.create<BasicUIComponentType<*, *>>(
      "com.intellij.openapi.ui.noria.BasicUIComponentTypeEP")

    private fun getTypes() = Extensions.getExtensions(EP_NAME)
    fun getComponents(): Map<String, BasicUIComponentType<*, *>> = getTypes().map { it.type to it }.toMap()
  }

  val type: String
  fun createNode(e: T): C
  fun update(info: UpdateInfo<C, T>)
  fun disposeNode(node: C) {}
}

@Suppress("UNCHECKED_CAST")
class SwingToolkit : Toolkit<JComponent> {
  val registry = BasicUIComponentType.getComponents()
  override fun isPrimitive(e: ElementType): Boolean = registry.containsKey(e.type)

  override fun createNode(e: Element): JComponent {
    val type: BasicUIComponentType<*, *> = registry[e.type.type] ?: throw IllegalStateException(
      "can't find component for type ${e.type.type}")
    type as BasicUIComponentType<JComponent, BaseProps>
    return type.createNode(e.props as BaseProps)
  }

  override fun performUpdates(l: List<Update<JComponent>>) {
    l.forEach { update ->
      when (update) {
        is AddChild ->
          update.parent.add(update.child, (update.childProps as BaseProps).constraints, update.index)
        is UpdateProps -> {
          val type = registry[update.type.type] ?: throw IllegalStateException("can't find component for type ${update.type.type}")
          type as BasicUIComponentType<JComponent, BaseProps>
          type.update(UpdateInfo(update.node, update.oldProps as BaseProps, update.newProps as BaseProps))
        }
        is RemoveChild -> update.parent.remove(update.child)
        is DestroyNode -> {
          val type = registry[update.type.type] ?: throw IllegalStateException("can't find component for type ${update.type.type}")
          type as BasicUIComponentType<JComponent, BaseProps>
          type.disposeNode(update.node)
        }
      }
    }
  }
}

class PanelComponentType : BasicUIComponentType<JPanel, Panel> {
  override val type: String = "panel"
  override fun createNode(e: Panel): JPanel =
    JPanel().apply {
      layout = e.layout
    }
  override fun update(info: UpdateInfo<JPanel, Panel>) {
  }
}

class LabelComponentType : BasicUIComponentType<JLabel, Label> {
  override val type: String = "label"
  override fun createNode(e: Label): JLabel = JLabel(e.text)
  override fun update(info: UpdateInfo<JLabel, Label>) {
    info.updateProp(Label::text, { text = it })
  }
}

val LISTENER = "noria.listener"

class ButtonComponentType : BasicUIComponentType<JButton, Button> {
  override val type: String = "button"
  override fun createNode(e: Button): JButton {
    val b = JButton(e.text)
    val newListener = ActionListener { e.onClick() }
    b.putClientProperty(LISTENER, newListener)
    b.addActionListener(newListener)
    return b
  }

  override fun update(info: UpdateInfo<JButton, Button>) {
    info.updateProp(Button::text, { text = it })
    updateListener(info, Button::onClick, info.new.onClick)
  }
}

private fun <T : BaseProps> updateListener(info: UpdateInfo<AbstractButton, T>,
                                           prop: KProperty1<T, *>,
                                           listener: () -> Unit) {
  if (prop.get(info.old) != prop.get(info.new)) {
    val oldListener = info.c.getClientProperty(LISTENER) as ActionListener?
    if (oldListener != null) {
      info.c.removeActionListener(oldListener)
    }
    val newListener = ActionListener { listener() }
    info.c.putClientProperty(LISTENER, newListener)
    info.c.addActionListener(newListener)
  }
}

class CheckboxComponentType : BasicUIComponentType<JCheckBox, Checkbox> {
  override val type: String = "checkbox"
  override fun createNode(e: Checkbox): JCheckBox {
    val c = if (e.focusable) JCheckBox() else NonFocusableCheckBox()
    c.text = e.text
    c.isSelected = e.selected
    c.isVisible = true
    c.isEnabled = e.enabled
    val l = ActionListener {
      e.onChange(c.isSelected)
    }
    c.putClientProperty(LISTENER, l)
    c.addActionListener(l)
    return c
  }

  override fun update(info: UpdateInfo<JCheckBox, Checkbox>) {
    info.updateProp(Checkbox::enabled, { isEnabled = it })
    info.updateProp(Checkbox::selected, { isSelected = it })
    info.updateProp(Checkbox::text, { text = it })
    updateListener(info, Checkbox::onChange, { info.new.onChange(info.c.isSelected) })
  }
}






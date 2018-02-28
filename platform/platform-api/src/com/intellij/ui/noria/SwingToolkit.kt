// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.noria

import com.intellij.ui.NonFocusableCheckBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.event.ActionListener
import javax.swing.*
import kotlin.reflect.KProperty1

data class UpdateInfo<out C : Any, out T : Any>(val c: C, val old: T, val new: T)

fun <C: Any, T : Any, R> UpdateInfo<C, T>.updateProp(p: KProperty1<T, R>, setter: C.(R) -> Unit) {
  if (p.get(old) != p.get(new)) {
    c.setter(p.get(new))
  }
}

@Suppress("UNCHECKED_CAST")
class SwingToolkit : Toolkit<JComponent> {
  val registry = PrimitiveComponentType.getComponents()
  override fun isPrimitive(e: ElementType): Boolean = registry.containsKey(e.type)

  override fun createNode(e: Element): JComponent {
    val type: PrimitiveComponentType<*, *> = registry[e.type.type] ?: throw IllegalStateException(
      "can't find component for type ${e.type.type}")
    type as PrimitiveComponentType<JComponent, BaseProps>
    return type.createNode(e.props as BaseProps)
  }

  override fun performUpdates(l: List<Update<JComponent>>,
                              root: JComponent) {
    l.forEach { update ->
      when (update) {
        is AddChild ->
          update.parent.add(update.child, (update.childProps as BaseProps).constraints, update.index)
        is UpdateProps -> {
          val type = registry[update.type.type] ?: throw IllegalStateException("can't find component for type ${update.type.type}")
          type as PrimitiveComponentType<JComponent, BaseProps>
          type.update(UpdateInfo(update.node, update.oldProps as BaseProps, update.newProps as BaseProps))
        }
        is RemoveChild ->
          update.parent.remove(update.child)
        is DestroyNode -> {
          val type = registry[update.type.type] ?: throw IllegalStateException("can't find component for type ${update.type.type}")
          type as PrimitiveComponentType<JComponent, BaseProps>
          type.disposeNode(update.node)
        }
      }
    }
    root.validate()
  }
}

class PanelComponentType : PrimitiveComponentType<JPanel, Panel> {
  override val type: String = PANEL_COMPONENT_TYPE
  override fun createNode(e: Panel): JPanel =
    JBPanel<JBPanel<*>>().apply {
      layout = e.layout
    }
  override fun update(info: UpdateInfo<JPanel, Panel>) {
  }
}

class LabelComponentType : PrimitiveComponentType<JLabel, Label> {
  override val type: String = LABEL_COMPONENT_TYPE
  override fun createNode(e: Label): JLabel = JBLabel(e.text)
  override fun update(info: UpdateInfo<JLabel, Label>) {
    info.updateProp(Label::text, { text = it })
  }
}

val LISTENER = "noria.listener"

class ButtonComponentType : PrimitiveComponentType<JButton, Button> {
  override val type: String = BUTTON_COMPONENT_TYPE
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

class CheckboxComponentType : PrimitiveComponentType<JCheckBox, Checkbox> {
  override val type: String = CHECKBOX_COMPONENT_TYPE
  override fun createNode(e: Checkbox): JCheckBox {
    val c = if (e.focusable) JBCheckBox() else NonFocusableCheckBox()
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






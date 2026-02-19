package com.intellij.execution.multilaunch.design.components

import com.intellij.icons.AllIcons
import com.intellij.util.PlatformIcons
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


/**
 * A class that decorates a given JComponent with a drop-down icon on the right side, imitating combobox visuals.
 */
@ApiStatus.Internal
open class DropDownDecorator() : JPanel(MigLayout("fillx, insets 0", "[left][right]")) {
  private val downIcon: JLabel = JLabel(PlatformIcons.COMBOBOX_ARROW_ICON)
  private var component: JComponent? = null
  //private val myContent: JLabel = JLabel()

  constructor(component: JComponent) : this() {
    setComponent(component)
  }

  init {
    add(downIcon, "align right")
  }

  fun setComponent(component: JComponent) {
    if (component == this.component) {
      return
    }

    this.component?.let {
      remove(it)
    }

    add(component, "align left", 0)
  }

  fun unsetIcon() {
    downIcon.icon = null
  }

  fun setRegularIcon() {
    downIcon.icon = PlatformIcons.COMBOBOX_ARROW_ICON
  }

  fun setSelectionIcon() {
    downIcon.icon = AllIcons.General.ArrowDown
  }

  override fun setForeground(color: Color?) {
    super.setForeground(color)
    // null when called from the constructor of the superclass
    component?.foreground = color
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    component?.isEnabled = enabled
    downIcon.isEnabled = enabled
  }
}

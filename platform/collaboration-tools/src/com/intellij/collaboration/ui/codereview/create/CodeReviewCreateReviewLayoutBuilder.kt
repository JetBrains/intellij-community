package com.intellij.collaboration.ui.codereview.create

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class CodeReviewCreateReviewLayoutBuilder {
  private var addSeparator = false

  private val reviewPanel: JPanel = JPanel(null).setListBackground().apply {
    layout = MigLayout(LC().gridGap("0", "0").insets("0").fill().flowY())
    isFocusCycleRoot = true
  }

  fun addComponent(component: JComponent,
                   cc: CC,
                   withoutBorder: Boolean = false,
                   withListBackground: Boolean = true): CodeReviewCreateReviewLayoutBuilder {
    reviewPanel.add(component, cc)

    if (withListBackground) component.setListBackground()

    if (!withoutBorder) {
      component.border = JBUI.Borders.empty(BASE_GAP)
    }

    if (addSeparator) {
      addSeparator = false
      component.border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                            component.border)
    }
    return this
  }

  fun addSeparator(): CodeReviewCreateReviewLayoutBuilder {
    addSeparator = true
    return this
  }


  private fun <T : JComponent> T.setListBackground(): T {
    this.background = UIUtil.getListBackground()
    return this
  }

  fun build(): JComponent {
    return reviewPanel
  }

  companion object {
    private const val BASE_GAP = 12
  }
}
package com.intellij.collaboration.ui.codereview.create

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.HideMode
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Use a static method to construct
 */
class CodeReviewCreateReviewLayoutBuilder @Internal constructor() {
  private var addSeparator = false

  private val componentsWithConstraints = mutableListOf<ComponentWithConstrains>()

  @Internal
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use a separate method without MigLayout constraints")
  fun addComponent(component: JComponent,
                   cc: CC,
                   withoutBorder: Boolean = false,
                   withListBackground: Boolean = true): CodeReviewCreateReviewLayoutBuilder {
    componentsWithConstraints.add(ComponentWithConstrains(component, cc))
    setupBorderAndBackground(component, withoutBorder, withListBackground)
    return this
  }

  fun addComponent(component: JComponent,
                   zeroMinWidth: Boolean = false,
                   stretchYWithWeight: Float? = null,
                   withoutBorder: Boolean = false,
                   withListBackground: Boolean = true): CodeReviewCreateReviewLayoutBuilder {
    val cc = CC().growX().pushX().apply {
      if (zeroMinWidth) {
        minWidth("0")
      }
      if (stretchYWithWeight != null) {
        growY(stretchYWithWeight).pushY(stretchYWithWeight)
      }
    }
    componentsWithConstraints.add(ComponentWithConstrains(component, cc))
    setupBorderAndBackground(component, withoutBorder, withListBackground)
    return this
  }

  fun addSeparator(): CodeReviewCreateReviewLayoutBuilder {
    addSeparator = true
    return this
  }

  private fun setupBorderAndBackground(component: JComponent, withoutBorder: Boolean, withListBackground: Boolean) {
    if (withListBackground) {
      component.setListBackground()
    }

    if (!withoutBorder) {
      component.border = JBUI.Borders.empty(BASE_GAP)
    }

    if (addSeparator) {
      addSeparator = false
      component.border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                            component.border)
    }
  }

  private fun <T : JComponent> T.setListBackground(): T {
    this.background = UIUtil.getListBackground()
    return this
  }

  fun build(): JComponent = JPanel(null).setListBackground().apply {
    layout = MigLayout(LC().gridGap("0", "0").insets("0").fill().flowY().hideMode(HideMode.DISREGARD))
    isFocusCycleRoot = true
    componentsWithConstraints.forEach { (c, cc) -> add(c, cc) }
  }

  private data class ComponentWithConstrains(
    val component: JComponent,
    val cc: CC
  )

  companion object {
    private const val BASE_GAP = 12

    fun create(): CodeReviewCreateReviewLayoutBuilder = CodeReviewCreateReviewLayoutBuilder()
  }
}
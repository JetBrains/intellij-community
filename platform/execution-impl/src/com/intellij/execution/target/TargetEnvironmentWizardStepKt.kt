// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

abstract class TargetEnvironmentWizardStepKt(@NlsContexts.DialogTitle title: String) : TargetEnvironmentWizardStep(title) {

  private val panel = object : ClearableLazyValue<JComponent>() {
    override fun compute(): JComponent = createPanel()
  }

  private val stepDescriptionLabel = JBLabel(ExecutionBundle.message("run.on.targets.wizard.step.description"))
  private val spinningLabel = JBLabel(AnimatedIcon.Default()).also {
    it.isVisible = false
  }

  protected var stepDescription: @Nls String
    @Nls get() = stepDescriptionLabel.text
    set(@Nls value) {
      stepDescriptionLabel.text = value
    }

  protected fun setSpinningVisible(visible: Boolean) {
    spinningLabel.isVisible = visible
    with(component) {
      revalidate()
      repaint()
    }
  }

  final override fun getComponent() = panel.value

  protected open fun createPanel(): JComponent {
    val result = JPanel(BorderLayout(HGAP, LARGE_VGAP))

    val top = createTopPanel()
    top.border = JBUI.Borders.merge(
      JBUI.Borders.emptyBottom(LARGE_VGAP),
      JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 1, 0),
      true
    )

    result.add(top, BorderLayout.NORTH)

    val center = createMainPanel()
    result.add(center, BorderLayout.CENTER)

    result.add(SeparatorComponent(), BorderLayout.SOUTH)

    return result
  }

  protected fun createTopPanel(): JComponent {
    return JPanel(HorizontalLayout(ICON_GAP)).also {
      it.border = JBUI.Borders.emptyBottom(VGAP)
      it.add(stepDescriptionLabel)
      spinningLabel.isVisible = false
      it.add(spinningLabel)
    }
  }

  protected open fun createMainPanel(): JComponent {
    return JPanel()
  }

  override fun dispose() {
    super.dispose()
    panel.drop()
  }

  companion object {
    @JvmStatic
    val ICON_GAP = JBUIScale.scale(6)

    @JvmStatic
    val HGAP = JBUIScale.scale(UIUtil.DEFAULT_HGAP)

    @JvmStatic
    val VGAP = JBUIScale.scale(UIUtil.DEFAULT_VGAP)

    @JvmStatic
    val LARGE_VGAP = JBUIScale.scale(UIUtil.LARGE_VGAP)

    @JvmStatic
    @Nls
    fun formatStepLabel(step: Int, totalSteps: Int = 3, @Nls message: String): String {
      @NlsSafe val description = "$step/$totalSteps. $message"
      return HtmlChunk.text(description).wrapWith(HtmlChunk.html()).toString()
    }
  }
}
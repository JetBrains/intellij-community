// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard

import com.intellij.facet.ui.FacetConfigurationQuickFix
import javax.swing.JButton
import javax.swing.JComponent

/**
 * Shows the quick fix of a validation result as a button.
 * Add [component] to the Swing hierarchy. Use on EDT only.
 *
 * The button runs the current fix on click, then calls [onFixApplied].
 */
internal class FixButton(private val onFixApplied: () -> Unit) {
  private var fix: FacetConfigurationQuickFix? = null
  val component: JComponent
    field = JButton().apply {
      isVisible = false
      addActionListener {
        val fix = fix ?: return@addActionListener
        fix.run(this)
        onFixApplied()
      }
    }

  /**
   * Shows the button for [fix].
   * Hides the button if [fix] is `null` or has no button text.
   */
  fun setFix(fix: FacetConfigurationQuickFix?) {
    val text = fix?.fixButtonText
    this.fix = if (text.isNullOrBlank()) null else fix
    component.text = text ?: ""
    component.isVisible = this.fix != null
  }
}

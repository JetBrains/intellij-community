// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard

import com.intellij.facet.ui.FacetConfigurationQuickFix
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JComponent

@TestApplication
@RunInEdt
internal class FixButtonTest {
  private var fixAppliedCount = 0
  private val fixButton = FixButton { fixAppliedCount++ }
  private val button = fixButton.component as JButton

  @Test
  fun `new button is hidden`() {
    assertThat(button.isVisible).isFalse()
  }

  @Test
  fun `fix with text shows button and runs fix on click`() {
    val fix = RecordingFix("Install")

    fixButton.setFix(fix)
    assertThat(button.isVisible).isTrue()
    assertThat(button.text).isEqualTo("Install")

    button.doClick(0)
    assertThat(fix.runCount).isEqualTo(1)
    assertThat(fix.place).isSameAs(button)
    assertThat(fixAppliedCount).isEqualTo(1)
  }

  @Test
  fun `fix without text hides button`() {
    fixButton.setFix(RecordingFix(null))
    assertThat(button.isVisible).isFalse()

    fixButton.setFix(RecordingFix(" "))
    assertThat(button.isVisible).isFalse()
  }

  @Test
  fun `null fix hides button and click does nothing`() {
    val fix = RecordingFix("Install")
    fixButton.setFix(fix)

    fixButton.setFix(null)
    assertThat(button.isVisible).isFalse()

    button.doClick(0)
    assertThat(fix.runCount).isZero()
    assertThat(fixAppliedCount).isZero()
  }

  @Test
  fun `new fix with other text replaces old fix`() {
    val oldFix = RecordingFix("Install")
    val newFix = RecordingFix("Download")
    fixButton.setFix(oldFix)
    fixButton.setFix(newFix)
    assertThat(button.text).isEqualTo("Download")

    button.doClick(0)
    assertThat(oldFix.runCount).isZero()
    assertThat(newFix.runCount).isEqualTo(1)
    assertThat(fixAppliedCount).isEqualTo(1)
  }

  @Test
  fun `new fix with same text replaces old fix`() {
    val oldFix = RecordingFix("Install")
    val newFix = RecordingFix("Install")
    fixButton.setFix(oldFix)
    fixButton.setFix(newFix)

    button.doClick(0)
    assertThat(oldFix.runCount).isZero()
    assertThat(newFix.runCount).isEqualTo(1)
    assertThat(fixAppliedCount).isEqualTo(1)
  }

  private class RecordingFix(text: String?) : FacetConfigurationQuickFix(text) {
    var runCount = 0
      private set
    var place: JComponent? = null
      private set

    override fun run(place: JComponent) {
      runCount++
      this.place = place
    }
  }
}

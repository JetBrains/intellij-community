// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.newclass

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class CreateWithTemplatesDialogPanelTest {
  @Test
  fun `test updates name field when selected template changes`() = runInEdtAndWait {
    val panel = TestDialogPanel()
    panel.setNameFieldToTemplateNameOnSelection()

    panel.selectTemplate(1)
    assertEquals("XYZ", panel.enteredName)
  }

  @Test
  fun `test name field is not updated when user inserted text`() = runInEdtAndWait {
    val panel = TestDialogPanel()
    panel.setNameFieldToTemplateNameOnSelection()

    panel.nameField.text = "MyClass"
    panel.selectTemplate(1)
    assertEquals("MyClass", panel.enteredName)
  }

  private class TestDialogPanel : CreateWithTemplatesDialogPanel(
    null,
    listOf(
      TemplatePresentation("Default", null, "Default"),
      TemplatePresentation("XYZ", null, "XYZ"),
    ),
  ) {
    fun selectTemplate(index: Int) {
      myTemplatesList.selectedIndex = index
    }
  }
}

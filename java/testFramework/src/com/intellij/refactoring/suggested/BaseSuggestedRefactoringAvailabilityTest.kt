// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.keymap.KeymapUtil

abstract class BaseSuggestedRefactoringAvailabilityTest : LightJavaCodeInsightFixtureTestCaseWithUtils() {
  protected abstract val fileType: LanguageFileType

  protected var ignoreErrors = false

  override fun setUp() {
    ignoreErrors = false
    super.setUp()
  }

  protected sealed class Availability {
    object NotAvailable : Availability()
    object Disabled : Availability()
    class Available(val tooltip: String) : Availability()
  }

  protected fun changeSignatureAvailableTooltip(name: String, usages: String): String {
    return buildString {
      append("Update $usages of '$name' to reflect signature change...")
      val shortcut = ActionManager.getInstance().getKeyboardShortcut("ShowIntentionActions")
      if (shortcut != null) {
        append(" (")
        append(KeymapUtil.getShortcutText(shortcut))
        append(")")
      }
    }
  }

  protected fun doTest(
    initialText: String,
    vararg editingActions: () -> Unit,
    expectedAvailability: Availability,
    expectedAvailabilityAfterResolve: Availability = expectedAvailability,
    wrapIntoCommandAndWriteActionAndCommitAll: Boolean = true
  ) {
    myFixture.configureByText(fileType, initialText)

    if (!ignoreErrors) {
      myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
    }

    executeEditingActions(editingActions, wrapIntoCommandAndWriteActionAndCommitAll)

    checkAvailability(expectedAvailability, afterResolve = false)
    checkAvailability(expectedAvailabilityAfterResolve, afterResolve = true)
  }

  private fun checkAvailability(expectedAvailability: Availability, afterResolve: Boolean) {
    if (afterResolve) {
      val intention = myFixture.availableIntentions.firstOrNull { it.familyName == "Suggested Refactoring" }
      assertEquals(expectedAvailability is Availability.Available, intention != null)
    }

    val availabilityIndicator = SuggestedRefactoringProviderImpl.getInstance(project).availabilityIndicator
    val iconRenderer = editor.markupModel.allHighlighters
      .map { it.gutterIconRenderer }
      .singleOrNull { it is RefactoringAvailableGutterIconRenderer || it is RefactoringDisabledGutterIconRenderer }
    when (expectedAvailability) {
      is Availability.NotAvailable -> {
        assertFalse(availabilityIndicator.hasData)
        assertNull(iconRenderer)
      }

      is Availability.Disabled -> {
        assertTrue(availabilityIndicator.hasData)
        assertTrue(iconRenderer is RefactoringDisabledGutterIconRenderer)
        assertEquals(SuggestedRefactoringAvailabilityIndicator.disabledRefactoringTooltip, iconRenderer?.tooltipText)
      }

      is Availability.Available -> {
        assertTrue(availabilityIndicator.hasData)
        assertTrue(iconRenderer is RefactoringAvailableGutterIconRenderer)
        assertEquals(expectedAvailability.tooltip, iconRenderer?.tooltipText)
      }
    }
  }
}

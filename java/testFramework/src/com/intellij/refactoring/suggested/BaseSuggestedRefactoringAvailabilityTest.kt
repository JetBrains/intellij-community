// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.refactoring.RefactoringBundle

abstract class BaseSuggestedRefactoringAvailabilityTest : LightJavaCodeInsightFixtureTestCaseWithUtils() {
  protected abstract val fileType: LanguageFileType

  protected var ignoreErrors: Boolean = false

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
    return RefactoringBundle.message(
      "suggested.refactoring.change.signature.gutter.icon.tooltip",
      usages,
      name,
      intentionActionShortcutHint
    )
  }

  protected fun renameAvailableTooltip(oldName: String, newName: String): String {
    return RefactoringBundle.message(
      "suggested.refactoring.rename.gutter.icon.tooltip",
      oldName,
      newName,
      intentionActionShortcutHint
    )
  }

  private val intentionActionShortcutHint by lazy {
    "(${KeymapUtil.getShortcutText(ActionManager.getInstance().getKeyboardShortcut("ShowIntentionActions")!!)})"
  }
  
  protected fun doTest(
    initialText: String,
    vararg editingActions: () -> Unit,
    expectedAvailability: Availability,
    expectedAvailabilityAfterResolve: Availability = expectedAvailability,
    expectedAvailabilityAfterBackgroundAmend: Availability = expectedAvailabilityAfterResolve,
    wrapIntoCommandAndWriteActionAndCommitAll: Boolean = true
  ) {
    myFixture.configureByText(fileType, initialText)

    if (!ignoreErrors) {
      myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
    }

    val provider = SuggestedRefactoringProviderImpl.getInstance(project)
    val amendStateInBackgroundSaved = provider._amendStateInBackgroundEnabled
    try {
      provider._amendStateInBackgroundEnabled = false

      executeEditingActions(editingActions, wrapIntoCommandAndWriteActionAndCommitAll)

      checkAvailability(expectedAvailability, afterResolve = false)
      checkAvailability(expectedAvailabilityAfterResolve, afterResolve = true)

      provider._amendStateInBackgroundEnabled = true

      checkAvailability(expectedAvailabilityAfterBackgroundAmend, afterResolve = true)
    }
    finally {
      provider._amendStateInBackgroundEnabled = amendStateInBackgroundSaved
    }
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

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
internal class IntentionsDocumentChangeTest {
  companion object {
    private val projectFixture: TestFixture<Project> = projectFixture(openAfterCreation = true)
  }

  private val myFixture: CodeInsightTestFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `semicolon fix disappears without moving the caret`(): Unit = timeoutRunBlocking {
    val text = "class Main { void main() { int value = 0\n} }"
    val offset = text.indexOf('\n')
    val cachedIntentions = withContext(Dispatchers.EDT) {
      val file = myFixture.addFileToProject("Main.java", text)
      myFixture.configureFromExistingVirtualFile(file.virtualFile)
      myFixture.editor.caretModel.moveToOffset(offset)
      myFixture.doHighlighting()
      IntentionsUI.getInstance(myFixture.project).getCachedIntentions(myFixture.editor, file).also {
        it.wrapAndUpdateActions(ShowIntentionsPass.getActionsToShow(myFixture.editor, file), false)
      }
    }
    val fix = cachedIntentions.errorFixes.single { it.text == "Insert ';'" }
    val showBulb = withContext(Dispatchers.EDT) { cachedIntentions.showBulb() }
    assertTrue(showBulb)

    val caretAfterFix = withContext(Dispatchers.EDT) {
      myFixture.launchAction(fix.action)
      myFixture.editor.caretModel.offset
    }
    assertEquals(offset, caretAfterFix)

    val fixesAfterHighlighting = withContext(Dispatchers.EDT) {
      myFixture.doHighlighting()
      val file = myFixture.file
      val refreshed = IntentionsUI.getInstance(myFixture.project).getCachedIntentions(myFixture.editor, file)
      refreshed.wrapAndUpdateActions(ShowIntentionsPass.getActionsToShow(myFixture.editor, file), false)
      refreshed.errorFixes.map { it.text }
    }
    assertFalse("Insert ';'" in fixesAfterHighlighting, fixesAfterHighlighting.toString())
    val result = withContext(Dispatchers.EDT) { myFixture.editor.document.text }
    assertEquals(text.replace("0\n", "0;\n"), result)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `obsolete fixes disappear while other intentions remain`(inspection: Boolean): Unit = timeoutRunBlocking {
    val keep = EmptyIntentionAction("Keep")
    val (changed, actions) = withContext(Dispatchers.EDT) {
      val file = myFixture.configureByText("test.txt", "text")
      val cached = CachedIntentions(myFixture.project, file, null)
      val info = ShowIntentionsPass.IntentionsInfo()
      val fix = HighlightInfo.IntentionActionDescriptor(EmptyIntentionAction("Fix"), null, null, null, null, null, null, null)
      if (inspection) {
        info.inspectionFixesToShow.add(fix)
      }
      else {
        info.errorFixesToShow.add(fix)
      }
      info.intentionsToShow.add(
        HighlightInfo.IntentionActionDescriptor(keep, null, null, null, null, null, null, null))
      cached.wrapAndUpdateActions(info, false)
      val changed = cached.wrapAndUpdateActions(ShowIntentionsPass.IntentionsInfo(), false)
      changed to cached.allActions.map { it.text }
    }
    assertTrue(changed)
    assertEquals(listOf(keep.text), actions)
  }
}

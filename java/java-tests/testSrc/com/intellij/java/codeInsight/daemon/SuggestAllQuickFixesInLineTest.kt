// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.daemon.impl.IntentionsUI
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.enableInspectionTool
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class SuggestAllQuickFixesInLineTest {
  companion object {
    private val projectFixture: TestFixture<LightJavaProject> = lightJavaProjectFixture()
  }

  private val myFixture: LightJavaCodeInsightTestFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testPreferFixesFromOffset(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    enableInspectionTool(myFixture.project, UnusedDeclarationInspection(true), myFixture.testRootDisposable)
    myFixture.configureByText("Foo.java",
                              "class Test {\n" +
                              "    int fo<caret>o; int bar = UnknownMethod();\n" +
                              "}")
    myFixture.doHighlighting()
    val allActions = readAction { computeIntentions().allActions }
    assertNotNull(allActions.first {  it.text == "Safe delete 'foo'" })
    assertNotNull(allActions.first {  it.text == "Create method 'UnknownMethod' in 'Test'" })

    withContext(Dispatchers.EDT) { myFixture.editor.caretModel.moveToOffset(myFixture.editor.caretModel.offset + 20) }
    myFixture.doHighlighting()
    val newActions = readAction { computeIntentions().allActions }
    assertNotNull(newActions.first {  it.text == "Create method 'UnknownMethod' in 'Test'" })
  }

  private fun computeIntentions(): CachedIntentions {
    val editor = myFixture.editor
    val file = myFixture.file
    return IntentionsUI.getInstance(myFixture.project).getCachedIntentions(editor, file).also {
      it.wrapAndUpdateActions(ShowIntentionsPass.getActionsToShow(editor, file), false)
    }
  }
}
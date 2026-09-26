package com.intellij.platform.lsp.unit

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class LspHoverInfoTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val docTargetProvider by extensionPointFixture(DocumentationTargetProvider.EP_NAME) {
    FakeDocumentationTargetProvider()
  }

  @Test
  fun `hover in text file`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    codeInsightFixture.configureByText("foo.txt", "foo\nbar")
    assertTrue(docTargetProvider.invocationLog.isEmpty())

    EditorMouseFixture(codeInsightFixture.editor as EditorImpl).moveTo(1, 2)
    // `hint.isVisible()` is `false` in `EditorMouseHoverPopupManager.getCurrentHint` because
    // `ApplicationManager.isHeadlessEnvironment()` is `true` in `AbstractPopup.show(java.awt.Component, int, int, boolean)`.
    // So, the real popup doesn't appear in test, and we can't assert `EditorMouseHoverPopupManager.isHintShown` to be `true`.
    waitUntilAssertSucceeds {
      assertEquals(listOf("foo.txt:6"), docTargetProvider.invocationLog)
    }
  }
}


private class FakeDocumentationTargetProvider : DocumentationTargetProvider {
  val invocationLog: MutableList<String> = mutableListOf()

  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    invocationLog.add("${file.name}:$offset")
    return emptyList()
  }
}

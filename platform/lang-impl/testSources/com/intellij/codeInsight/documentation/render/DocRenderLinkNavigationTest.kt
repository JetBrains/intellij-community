// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.lang.documentation.ide.DocumentationLinkNavigatable
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.model.Pointer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Point

@TestApplication
class DocRenderLinkNavigationTest {
  private val project = projectFixture()
  private val sourceRoot = project.moduleFixture().sourceRootFixture()
  private val file = sourceRoot.psiFileFixture("links.txt", "Documentation\nDeclaration")
  private val editor by file.editorFixture()

  @Suppress("unused")
  private val linkHandler = extensionPointFixture(DocumentationLinkHandler.EP_NAME, LoadingOrder.FIRST) {
    object : DocumentationLinkHandler {
      override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult =
        LinkResolveResult.resolvedTarget(target)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `navigation preserves the rendered reference position`(inline: Boolean): Unit = timeoutRunBlocking {
    val navigation = CompletableDeferred<Pair<Editor, Point>?>()
    val navigatable = object : DocumentationLinkNavigatable {
      override fun canNavigate(): Boolean = true
      override fun canNavigateToSource(): Boolean = true

      override fun navigate(requestFocus: Boolean) {
        navigation.complete(null)
      }

      override fun navigate(editor: Editor, popupPosition: Point) {
        navigation.complete(editor to popupPosition)
      }
    }

    val target = mock<DocumentationTarget> {
      on { this.navigatable } doReturn navigatable
    }
    whenever(target.createPointer())
      .thenReturn(Pointer.hardPointer(target))

    val position = Point(42, 73)
    withContext(Dispatchers.EDT) {
      val manager = DocumentationManager.getInstance(project.get())
      if (inline)
        manager.navigateInlineLink("psi_element://cref://0", editor, position) { target }
      else
        manager.navigateInlineLink("psi_element://cref://0") { target }
    }
    val actual = navigation.await()
    if (inline) {
      assertSame(editor, actual?.first)
      assertEquals(position, actual?.second)
    }
    else {
      assertNull(actual)
    }
  }
}

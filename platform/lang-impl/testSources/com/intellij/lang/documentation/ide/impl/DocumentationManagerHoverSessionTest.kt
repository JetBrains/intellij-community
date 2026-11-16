// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.model.Pointer
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import javax.swing.JPanel

@TestApplication
class DocumentationManagerHoverSessionTest {
  private val project = projectFixture()

  @Test
  fun `a request that goes to the tool window returns no session`(): Unit = timeoutRunBlocking {
    val request = readAction { target().documentationRequest() }
    val oldSkipPopup = DocumentationManager.skipPopup
    DocumentationManager.skipPopup = true
    try {
      withContext(Dispatchers.UI) {
        var done = false
        val session = DocumentationManager.getInstance(project.get())
          .showDocumentationOnHoverAroundByRequests(listOf(request), project.get(), JPanel(), Rectangle(), 0, 0) { done = true }
        assertNull(session, "A request without a popup must not return a session")
        assertFalse(done, "The done callback must not run for a request without a popup")
      }
    }
    finally {
      DocumentationManager.skipPopup = oldSkipPopup
    }
  }

  private fun target(): DocumentationTarget = object : DocumentationTarget {
    override fun createPointer() = Pointer.hardPointer(this)
    override fun computePresentation() = TargetPresentation.builder("target").presentation()
    override fun computeDocumentation() = null
  }
}

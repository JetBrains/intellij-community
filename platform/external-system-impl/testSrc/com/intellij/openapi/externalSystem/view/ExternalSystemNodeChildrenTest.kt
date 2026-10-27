// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.view

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class ExternalSystemNodeChildrenTest {
  @Test
  fun `a cancelled build leaves no children`() {
    val structure = mock<ExternalProjectsStructure>()
    val view = mock<ExternalProjectsView>()
    whenever(view.structure).thenReturn(structure)

    val cancelOnce = AtomicBoolean(true)
    val node = object : ExternalSystemNode<Any>(view, null) {
      override fun doBuildChildren(): List<ExternalSystemNode<*>> = listOf(CancellingNode(view, cancelOnce), LeafNode(view))
    }

    // The first build is cancelled in the visibility check, as a read action is cancelled by a write action.
    assertThrows<ProcessCanceledException> { node.children }
    assertEquals(2, node.children.size, "The retry has the children of one build")
  }

  private class LeafNode(view: ExternalProjectsView) : ExternalSystemNode<Any>(view, null)

  private class CancellingNode(view: ExternalProjectsView, private val cancelOnce: AtomicBoolean) : ExternalSystemNode<Any>(view, null) {
    override fun isVisible(): Boolean {
      if (cancelOnce.getAndSet(false)) throw ProcessCanceledException()
      return super.isVisible()
    }
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.fileStructure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.rpc.rpcId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData
import com.intellij.ide.util.treeView.smartTree.TreeActionWithDefaultState
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.mock.Mock
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.project.projectId
import com.intellij.platform.structureView.impl.StructureTreeApi
import com.intellij.platform.structureView.impl.dto.NodeProviderNodesDto
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class FileStructureNodeProviderTest {
  private val projectFixture = projectFixture()

  @Test
  fun `fresh providers keep initially enabled nodes out of main nodes`() = checkProviderNodes(initiallyEnabled = true)

  @Test
  fun `fresh providers keep deferred nodes out of main nodes`() = checkProviderNodes(initiallyEnabled = false)

  private fun checkProviderNodes(initiallyEnabled: Boolean): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val api = StructureTreeApi.getInstance()
    val projectId = project.projectId()
    val file = LightVirtualFile("structure.txt")
    val id = StructureViewDtoId(1)
    val editor = object : Mock.MyFileEditor() {
      override fun getStructureViewBuilder() = object : TreeBasedStructureViewBuilder() {
        override fun createStructureViewModel(editor: Editor?): StructureViewModel =
          object : TextEditorBasedStructureViewModel(null as Editor?) {
            private val root = Element("root", Element("own"))
            override fun getRoot(): StructureViewTreeElement = root
            override fun getNodeProviders() = listOf(
              Provider("first", initiallyEnabled),
              Provider("second", initiallyEnabled),
            )
          }
      }
    }
    try {
      val dto = checkNotNull(api.createStructureViewModel(projectId, id, editor.rpcId(), file.rpcId()))
      val nodes = dto.nodes.toFlow().filterNotNull().first()
      assertEquals(listOf("own"), nodes.nodes.map { it.presentation.presentableText })
      if (initiallyEnabled) {
        checkGroups(nodes.nodeProviders)
        assertNull(nodes.deferredProviderNodes.await())
      }
      else {
        assertTrue(nodes.nodeProviders.isEmpty())
        val deferred = checkNotNull(nodes.deferredProviderNodes.await())
        assertEquals(listOf("own"), deferred.nodes.map { it.presentation.presentableText })
        checkGroups(deferred.nodeProviders)
      }
    }
    finally {
      api.structureViewModelDisposed(projectId, id)
      withContext(Dispatchers.EDT) { editor.dispose() }
    }
  }

  private fun checkGroups(providers: List<NodeProviderNodesDto>) {
    assertEquals(listOf("first", "second"), providers.map { it.providerName })
    for (provider in providers) {
      assertEquals(listOf(provider.providerName, "${provider.providerName}.nested"), provider.nodes.map { it.presentation.presentableText })
      assertEquals(provider.nodes.first().id, provider.nodes.last().parentId)
    }
  }

  private class Provider(private val id: String, private val enabled: Boolean) :
    FileStructureNodeProvider<StructureViewTreeElement>, TreeActionWithDefaultState, DumbAware {
    override fun getName(): String = id
    override fun getPresentation() = ActionPresentationData(id, id, EmptyIcon.ICON_16)
    override fun getCheckBoxText(): String = id
    override fun getShortcut(): Array<Shortcut> = emptyArray()
    override fun isEnabledByDefault(): Boolean = enabled
    override fun provideNodes(node: TreeElement): Collection<StructureViewTreeElement> =
      if ((node as StructureViewTreeElement).value == "root") listOf(Element(id, Element("$id.nested"))) else emptyList()
  }

  private class Element(private val text: String, private vararg val children: StructureViewTreeElement) : StructureViewTreeElement {
    override fun getValue(): Any = text
    override fun getPresentation() = PresentationData(text, null, null, null)
    override fun getChildren(): Array<out StructureViewTreeElement> = children
    override fun navigate(requestFocus: Boolean) {}
    override fun canNavigate(): Boolean = false
    override fun canNavigateToSource(): Boolean = false
  }
}

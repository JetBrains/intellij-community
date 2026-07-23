// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView

import com.intellij.ide.impl.StructureViewWrapperImpl
import com.intellij.ide.structureView.impl.StructureViewFactoryImpl
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.Grouper
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.mock.Mock
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class StructureViewFactoryTest : HeavyPlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testRefreshReevaluatesSelectedEditorsStructureViewBuilder() {
    val toolWindowManager = TestToolWindowManager(project)
    project.replaceService(ToolWindowManager::class.java, toolWindowManager, testRootDisposable)

    val file = LightVirtualFile("dynamic.structure")
    val editor = DynamicStructureFileEditor(file)
    project.replaceService(FileEditorManager::class.java, TestFileEditorManager(file, editor), testRootDisposable)

    val factory = StructureViewFactoryImpl(project, (project as ComponentManagerEx).getCoroutineScope())
    factory.initToolWindow(toolWindowManager.structureToolWindow)
    val wrapper = factory.structureViewWrapper as StructureViewWrapperImpl

    factory.refreshStructureView()
    assertTrue("The initial structure view builder was not queried", editor.builderQueried.await(10, TimeUnit.SECONDS))
    assertNull(wrapper.getStructureView())

    editor.isStructureAvailable = true
    factory.refreshStructureView()
    assertTrue("The dynamically available structure view was not installed", editor.structureView.installed.await(10, TimeUnit.SECONDS))
    assertSame(editor.structureView, wrapper.getStructureView())
  }
}

private class TestToolWindowManager(project: Project) : ToolWindowHeadlessManagerImpl(project) {
  val structureToolWindow: ToolWindow = object : MockToolWindow(project) {
    override fun getId(): String = ToolWindowId.STRUCTURE_VIEW

    override fun isVisible(): Boolean = true
  }

  override fun getToolWindow(id: String?): ToolWindow? {
    return if (id == ToolWindowId.STRUCTURE_VIEW) structureToolWindow else super.getToolWindow(id)
  }
}

private class TestFileEditorManager(
  private val file: VirtualFile,
  private val editor: FileEditor,
) : Mock.MyFileEditorManager() {
  private val provider = object : Mock.MyFileEditorProvider() {
    override fun getEditorTypeId(): String = "dynamic-structure-test"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE
  }

  override fun getSelectedFiles(): Array<VirtualFile> = arrayOf(file)

  override fun getSelectedEditors(): Array<FileEditor> = arrayOf(editor)

  override fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider? {
    return FileEditorWithProvider(editor, provider).takeIf { file == this.file }
  }
}

private class DynamicStructureFileEditor(private val file: VirtualFile) : Mock.MyFileEditor() {
  val builderQueried = CountDownLatch(1)
  val structureView = TestStructureView()

  @Volatile
  var isStructureAvailable: Boolean = false

  private val builder = object : StructureViewBuilder {
    override fun createStructureView(fileEditor: FileEditor?, project: Project): StructureView {
      return structureView
    }
  }

  override fun getComponent(): JComponent = JPanel()

  override fun getName(): String = "Dynamic Structure"

  override fun getFile(): VirtualFile = file

  override fun getStructureViewBuilder(): StructureViewBuilder? {
    builderQueried.countDown()
    return builder.takeIf { isStructureAvailable }
  }
}

private class TestStructureView : StructureView {
  val installed = CountDownLatch(1)
  private val model = TestStructureViewModel()

  override fun navigateToSelectedElement(requestFocus: Boolean): Boolean = false

  override fun getComponent(): JComponent = JPanel()

  override fun centerSelectedRow() {
  }

  override fun restoreState() {
    installed.countDown()
  }

  override fun storeState() {
  }

  override fun getTreeModel(): StructureViewModel = model

  override fun dispose() {
    Disposer.dispose(model)
  }
}

private class TestStructureViewModel : StructureViewModel {
  private val root = object : StructureViewTreeElement {
    override fun getValue(): Any = "root"

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
      override fun getPresentableText(): String = "root"

      override fun getIcon(unused: Boolean): Icon? = null
    }

    override fun getChildren(): Array<StructureViewTreeElement> = StructureViewTreeElement.EMPTY_ARRAY

    override fun navigate(requestFocus: Boolean) {
    }

    override fun canNavigate(): Boolean = false

    override fun canNavigateToSource(): Boolean = false
  }

  override fun getRoot(): StructureViewTreeElement = root

  override fun getGroupers(): Array<Grouper> = Grouper.EMPTY_ARRAY

  override fun getSorters(): Array<Sorter> = Sorter.EMPTY_ARRAY

  override fun getFilters(): Array<Filter> = Filter.EMPTY_ARRAY

  override fun getCurrentEditorElement(): Any? = null

  override fun addEditorPositionListener(listener: FileEditorPositionListener) {
  }

  override fun removeEditorPositionListener(listener: FileEditorPositionListener) {
  }

  override fun addModelListener(modelListener: ModelListener) {
  }

  override fun removeModelListener(modelListener: ModelListener) {
  }

  override fun shouldEnterElement(element: Any?): Boolean = false

  override fun dispose() {
  }
}

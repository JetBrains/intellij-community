// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.*
import com.intellij.projectView.BaseProjectViewTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.tree.TreeUtil
import java.nio.file.Path

class WorkspaceProjectTreeStructureTest: BaseProjectViewTestCase() {

  override fun getTestDirectoryName() = "workspace"

  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
    return Path.of(testDataPath, testPath, testDirectoryName, "workspace")
  }

  override fun setUpModule() {
  }

  override fun setUp() {
    super.setUp()
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable)
  }

  fun testStructure() {
    val rootElement = myStructure.rootElement as AbstractTreeNode<*>
    val workspace = myStructure.getChildElements(rootElement).filterIsInstance<PsiDirectoryNode>().first()
    assertStructureEqual("Workspace: workspace\n" +
                         " Subproject: untitled\n" +
                         "  HtmlFile:foo.html\n" +
                         " Subproject: untitled1\n" +
                         "  HtmlFile:bar.html", workspace)
  }

  fun testActions() {
    val projectView = ProjectViewImpl.getInstance(project) as ProjectViewImpl
    val tree = projectView.currentProjectViewPane.tree
    PlatformTestUtil.waitForPromise(TreeUtil.promiseExpandAll(tree))

    tree.setSelectionRow(0)
    val context = DataManager.getInstance().getDataContext(projectView.component)
    assertTrue(isActionEnabled(context))

    tree.setSelectionRow(1)
    val context1 = DataManager.getInstance().getDataContext(projectView.component)
    assertFalse(isActionEnabled(context1))
  }

  private fun isActionEnabled(context: DataContext): Boolean {
    val action = ActionManager.getInstance().getAction("AddToWorkspace")
    val presentation = Presentation()
    val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.PROJECT_VIEW_POPUP, presentation, context)
    action.update(actionEvent)
    return presentation.isEnabled
  }
}
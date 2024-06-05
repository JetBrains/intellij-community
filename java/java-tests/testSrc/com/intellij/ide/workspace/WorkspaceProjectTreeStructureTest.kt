// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.projectView.BaseProjectViewTestCase
import java.nio.file.Path

class WorkspaceProjectTreeStructureTest: BaseProjectViewTestCase() {

  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
    return Path.of(testDataPath, testPath, testDirectoryName, "workspace")
  }

  override fun setUpModule() {
  }

  fun testWorkspace() {
    val rootElement = myStructure.rootElement as AbstractTreeNode<*>
    val workspace = myStructure.getChildElements(rootElement).filterIsInstance<PsiDirectoryNode>().first()
    assertStructureEqual("Workspace: workspace\n" +
                         " Subproject: untitled\n" +
                         "  HtmlFile:foo.html\n" +
                         " Subproject: untitled1\n" +
                         "  HtmlFile:bar.html", workspace)
  }
}
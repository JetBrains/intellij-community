// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.PackageViewPane
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.psi.search.scope.ProblemsScope
import com.intellij.psi.search.scope.ProjectFilesScope
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestSourceBasedTestCase
import com.intellij.ui.tree.TreeTestUtil
import javax.swing.JTree

abstract class AbstractProjectViewTest : TestSourceBasedTestCase() {
  override fun getTestPath(): String? = null

  protected val projectView: ProjectViewImpl
    get() = ProjectView.getInstance(project) as ProjectViewImpl

  protected val currentSettings: ProjectViewSettings
    get() = ProjectViewSettings.Delegate(project, projectView.currentViewId)

  protected val currentPane: AbstractProjectViewPane
    get() = projectView.currentProjectViewPane

  protected val currentTree: JTree
    get() = currentPane.tree

  protected fun createTreeTest() = TreeTestUtil(currentTree)

  protected fun waitWhileBusy() = PlatformTestUtil.waitWhileBusy(currentTree)

  protected fun selectProjectPane() = selectProjectViewPane(ProjectViewPane.ID, null)

  protected fun selectPackagesPane() = selectProjectViewPane(PackageViewPane.ID, null)

  protected fun selectProjectFilesPane() = selectScopeViewPane(ProjectFilesScope.INSTANCE)

  protected fun selectProblemsPane() = selectScopeViewPane(ProblemsScope.INSTANCE)

  protected fun selectScopeViewPane(scope: NamedScope) = selectProjectViewPane(ScopeViewPane.ID, scope.toString() + "; " + scope.javaClass)

  private fun selectProjectViewPane(id: String, subId: String?) {
    PlatformTestUtil.waitForCallback(projectView.changeViewCB(id, subId))
    assertEquals(id, currentPane.id)
    if (subId != null) assertEquals(subId, currentPane.subId)
    waitWhileBusy()
  }
}

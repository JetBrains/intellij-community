// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.PackageViewPane
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.scope.ProjectFilesScope
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestSourceBasedTestCase
import com.intellij.ui.tree.TreeTestUtil
import javax.swing.JTree

abstract class AbstractProjectViewTest : TestSourceBasedTestCase() {
  private var originalNestingRules: MutableList<NestingRule>? = null

  override fun tearDown() {
    originalNestingRules?.let { ProjectViewFileNestingService.getInstance().rules = it }
    super.tearDown()
  }

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

  protected fun selectScopeViewPane(scope: NamedScope) = selectProjectViewPane(ScopeViewPane.ID, scope.toString() + "; " + scope.javaClass)

  private fun selectProjectViewPane(id: String, subId: String?) {
    PlatformTestUtil.waitForCallback(projectView.changeViewCB(id, subId))
    assertEquals(id, currentPane.id)
    if (subId != null) assertEquals(subId, currentPane.subId)
    waitWhileBusy()
  }

  protected fun selectFile(file: VirtualFile) {
    waitWhileBusy()
    currentPane.select(null, file, false)
  }

  protected fun selectElement(element: PsiElement) {
    waitWhileBusy()
    currentPane.select(element, PsiUtil.getVirtualFile(element), false)
  }

  protected fun deleteElement(element: PsiElement) {
    WriteCommandAction.runWriteCommandAction(null) { element.delete() }
  }

  protected fun renameElement(element: PsiElement, name: String) {
    RenameProcessor(project, element, name, false, false).run()
  }

  protected fun moveElementToPackage(element: PsiElement, qualifiedName: String) {
    val psi = javaFacade.findPackage(qualifiedName)!!
    val destination = SingleSourceRootMoveDestination(PackageWrapper.create(psi), psi.directories[0])
    MoveClassesOrPackagesProcessor(project, arrayOf(element), destination, false, false, null).run()
  }

  protected fun movePackageToPackage(srcQualifiedName: String, dstQualifiedName: String) {
    val psi = javaFacade.findPackage(srcQualifiedName)!!
    moveElementToPackage(psi, dstQualifiedName)
  }

  protected fun setFileNestingRules(vararg rules: Pair<String, String>) {
    val nesting = ProjectViewFileNestingService.getInstance()
    if (originalNestingRules == null) originalNestingRules = nesting.rules
    nesting.rules = rules.map { NestingRule(it.first, it.second) }
    currentPane.updateFromRoot(false)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView

import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightTestNamedSubFixture
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.PackageViewPane
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.isSelectInProjectViewServiceBusy
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectView.TestSourceBasedFixture
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.scope.ProjectFilesScope
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.tree.TreeTestUtil
import org.junit.jupiter.api.Assertions.assertEquals
import javax.swing.JTree

/**
 * A [TestSourceBasedFixture] with the helpers of the project view tool window: the panes, the selection, and the refactorings
 * that change the tree. It replaces `AbstractProjectViewTest`.
 *
 * The helpers that use a pane or a refactoring run on the EDT. The test thread calls them.
 */
class ProjectViewPaneFixture(context: Context, testPath: String?, iconRequired: Boolean)
  : TestSourceBasedFixture(context, null, testPath, null, iconRequired) {
  private var originalNestingRules: List<NestingRule>? = null

  val projectView: ProjectViewImpl
    get() = ProjectView.getInstance(project) as ProjectViewImpl

  val currentSettings: ProjectViewSettings
    get() = ProjectViewSettings.Delegate(project, projectView.currentViewId)

  val currentPane: AbstractProjectViewPane
    get() = projectView.currentProjectViewPane

  val currentTree: JTree
    get() = currentPane.tree

  fun createTreeTest(): TreeTestUtil = TreeTestUtil(currentTree)

  /**
   * Checks the structure of the tree of [test] on the EDT.
   */
  fun assertStructure(test: TreeTestUtil, expected: String) {
    runInEdtAndWait { test.assertStructure(expected) }
  }

  /**
   * Returns the text of the tree of [test], read on the EDT.
   */
  fun print(test: TreeTestUtil): String = runInEdtAndGet { test.toString() }

  /**
   * Creates the class [className] in the first directory of the package [packageName], on the EDT.
   */
  fun createClassInPackage(packageName: String, className: String): PsiClass = runInEdtAndGet {
    JavaDirectoryService.getInstance().createClass(javaFacade.findPackage(packageName)!!.directories[0], className)
  }

  fun waitWhileBusy() {
    runInEdtAndWait { PlatformTestUtil.waitWhileBusy(currentTree) }
  }

  private fun waitWhileSelectInServiceBusy() = PlatformTestUtil.waitWhileBusy { isSelectInProjectViewServiceBusy(project) }

  fun selectProjectPane() = selectProjectViewPane(ProjectViewPane.ID, null)

  fun selectPackagesPane() = selectProjectViewPane(PackageViewPane.ID, null)

  fun selectProjectFilesPane() = selectScopeViewPane(ProjectFilesScope.INSTANCE)

  fun selectScopeViewPane(scope: NamedScope) = selectProjectViewPane(ScopeViewPane.ID, scope.toString() + "; " + scope.javaClass)

  private fun selectProjectViewPane(id: String, subId: String?) {
    runInEdtAndWait {
      PlatformTestUtil.waitForCallback(projectView.changeViewCB(id, subId))
      assertEquals(id, currentPane.id)
      if (subId != null) assertEquals(subId, currentPane.subId)
      PlatformTestUtil.waitWhileBusy(currentTree)
    }
  }

  fun selectFile(file: VirtualFile) {
    runInEdtAndWait {
      PlatformTestUtil.waitWhileBusy(currentTree)
      currentPane.select(null, file, false)
      waitWhileSelectInServiceBusy()
    }
  }

  fun selectElement(element: PsiElement) {
    runInEdtAndWait {
      PlatformTestUtil.waitWhileBusy(currentTree)
      currentPane.select(element, PsiUtil.getVirtualFile(element), false)
      waitWhileSelectInServiceBusy()
    }
  }

  fun deleteElement(element: PsiElement) {
    WriteCommandAction.runWriteCommandAction(null) { element.delete() }
  }

  fun renameElement(element: PsiElement, name: String) {
    runInEdtAndWait { RenameProcessor(project, element, name, false, false).run() }
  }

  fun moveElementToPackage(element: PsiElement, qualifiedName: String) {
    runInEdtAndWait {
      val psi = javaFacade.findPackage(qualifiedName)!!
      val destination = SingleSourceRootMoveDestination(PackageWrapper.create(psi), psi.directories[0])
      MoveClassesOrPackagesProcessor(project, arrayOf(element), destination, false, false, null).run()
    }
  }

  fun movePackageToPackage(srcQualifiedName: String, dstQualifiedName: String) {
    val psi = ReadAction.computeBlocking<PsiElement, RuntimeException> { javaFacade.findPackage(srcQualifiedName)!! }
    moveElementToPackage(psi, dstQualifiedName)
  }

  fun setFileNestingRules(vararg rules: Pair<String, String>) {
    val nesting = ProjectViewFileNestingService.getInstance()
    if (originalNestingRules == null) originalNestingRules = nesting.getRules()
    nesting.setRules(rules.map { NestingRule(it.first, it.second) })
    runInEdtAndWait { currentPane.updateFromRoot(false) }
  }

  override fun tearDown() {
    try {
      originalNestingRules?.let { ProjectViewFileNestingService.getInstance().setRules(it) }
    }
    finally {
      super.tearDown()
    }
  }
}

/**
 * Creates a [ProjectViewPaneFixture] with a new project for each test. Declare it as an instance field.
 * [testPath] is `getTestPath()` of the JUnit 3 test. By default, the fixture copies no test data.
 */
fun projectViewPaneFixture(testPath: String? = null, iconRequired: Boolean = false): TestFixture<ProjectViewPaneFixture> =
  heavyJavaCodeInsightTestNamedSubFixture("projectViewPaneFixture", directoryBasedProject = false) { context ->
    ProjectViewPaneFixture(context, testPath, iconRequired)
  }

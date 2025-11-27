// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.UiInterceptors

class RenameClassInDumbModeTest : LightJavaCodeInsightFixtureTestCase() {

  fun `test rename class in dumb mode renames only parent file`() {
    myFixture.configureByText("TestClass.java", "public class TestClass {}")

    val psiFile = myFixture.file as PsiJavaFile
    val psiClass = psiFile.classes[0]
    assertNotNull("PsiClass should not be null", psiClass)

    interceptRenameDialogAndInvokeRename("RenamedClass.java")

    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      runInEdtAndWait {
        RenameElementAction().actionPerformed(createEvent(project, psiClass))
      }
    }


    // class name is expected to remain the same, only file name is changed
    assertEquals("TestClass", psiClass.name)
    assertEquals("RenamedClass.java", psiFile.name)
  }

  fun `test rename class in dumb mode does not rename when class name differs from file`() {
    // can't test actual rename, because test will fail because of "no dialog shown"
    // this should test essentially the same

    myFixture.configureByText("FileName.java", "public class DifferentClass {}")

    val psiFile = myFixture.file as PsiJavaFile
    val psiClass = psiFile.classes[0]
    assertNotNull("PsiClass should not be null", psiClass)

    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      val dataContext = createEvent(project, psiClass).dataContext
      val handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(dataContext)
      val usableHandlers = handlers.filter { DumbService.getInstance(project).isUsableInCurrentContext(it) }
      assertTrue("Rename should be unavailable in dumb mode when class name differs from file", usableHandlers.isEmpty())
    }
  }

  private fun interceptRenameDialogAndInvokeRename(newName: String) {
    UiInterceptors.register(object : UiInterceptors.UiInterceptor<RenameDialog>(RenameDialog::class.java) {
      override fun doIntercept(component: RenameDialog) {
        component.performRename(newName)
      }
    })
  }

  private fun createEvent(project: Project, psiElement: PsiElement): AnActionEvent {
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.PSI_ELEMENT, psiElement)
      .build()
    return TestActionEvent.createTestEvent(context)
  }
}

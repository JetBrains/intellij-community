// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.actions.RenameFileAction
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.UiInterceptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class RenameClassInDumbModeTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    Registry.get("rename.files.in.dumb.mode.enable").setValue(true, myFixture.getTestRootDisposable())
  }

  @Test
  fun `test rename class in dumb mode renames only parent file`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("TestClass.java", "public class TestClass {}")

    val psiFile = myFixture.file as PsiJavaFile
    val psiClass = readAction { psiFile.classes[0] }
    assertNotNull(psiClass, "PsiClass should not be null")

    interceptRenameDialogAndInvokeRename("RenamedClass.java")

    withContext(Dispatchers.EDT) {
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
        RenameElementAction().actionPerformed(createEvent(myFixture.project, psiClass))
      }

      // class name is expected to remain the same, only file name is changed
      assertEquals("TestClass", psiClass.name)
      assertEquals("RenamedClass.java", psiFile.name)
    }
  }

  @Test
  fun `test rename file action works in dumb mode`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("FileName.java", "public class DifferentClass {}")

    val psiFile = myFixture.file as PsiJavaFile
    val psiClass = readAction { psiFile.classes[0] }
    assertNotNull(psiClass, "PsiClass should not be null")

    interceptRenameDialogAndInvokeRename("RenamedClass.java")
    withContext(Dispatchers.EDT) {
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
        RenameFileAction().actionPerformed(createEvent(myFixture.project, psiClass))
      }

      // class name is expected to remain the same, "rename file action" only renames file
      assertEquals("DifferentClass", psiClass.name)
      assertEquals("RenamedClass.java", psiFile.name)
    }
  }

  @Test
  fun `test rename class in dumb mode does not rename when class name differs from file`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    // can't test actual rename, because test will fail because of "no dialog shown"
    // this should test essentially the same

    myFixture.configureByText("FileName.java", "public class DifferentClass {}")

    val psiFile = myFixture.file as PsiJavaFile
    val psiClass = readAction { psiFile.classes[0] }
    assertNotNull(psiClass, "PsiClass should not be null")

    withContext(Dispatchers.EDT) {
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
        val dataContext = createEvent(myFixture.project, psiClass).dataContext
        val handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(dataContext)
        val usableHandlers = handlers.filter { DumbService.getInstance(myFixture.project).isUsableInCurrentContext(it) }
        assertTrue(usableHandlers.isEmpty(), "Rename should be unavailable in dumb mode when class name differs from file, psiFile=${psiFile.name}, psiClass=${psiClass.name}")
      }
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
      .add(CommonDataKeys.PROJECT, myFixture.project)
      .add(CommonDataKeys.PSI_ELEMENT, psiElement)
      .build()
    return TestActionEvent.createTestEvent(context)
  }
}

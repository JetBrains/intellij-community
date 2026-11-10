// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.intention.impl.CreateClassInPackageInModuleFix
import com.intellij.codeInspection.java19modules.JavaModuleDefinitionInspection
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class CreateClassInPackageInModuleTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(JavaModuleDefinitionInspection())
  }

  @Test
  fun testExportsMissingDir(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMissingDir("exports") }
  @Test
  fun testOpensMissingDir(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestMissingDir("opens") }

  @Test
  fun testExportsEmptyDir(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestEmptyDir("exports") }
  @Test
  fun testOpensEmptyDir(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestEmptyDir("opens") }

  @Test
  fun testExportsInterface(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestInterface("exports") }
  @Test
  fun testOpensInterface(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestInterface("opens") }

  private suspend fun doTestMissingDir(keyword: String) {
    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { $keyword foo.bar.<caret>missing; }") as PsiJavaFile
    doAction("foo.bar.missing", moduleInfo)
  }

  private suspend fun doTestEmptyDir(keyword: String) {
    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { $keyword foo.bar.<caret>empty; }") as PsiJavaFile
    val dir = myFixture.tempDirFixture.findOrCreateDir("foo.bar.empty")
    assertNotNull(dir)
    doAction("foo.bar.empty", moduleInfo)
  }

  private suspend fun doTestInterface(keyword: String) {
    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { $keyword foo.bar.<caret>missing; }") as PsiJavaFile
    doAction("foo.bar.missing", moduleInfo, isInterface = true, name = "MyInterface")
  }

  private suspend fun doAction(packageName: String, moduleInfo: PsiJavaFile,
                               isInterface: Boolean = false, name: String = "MyClass") {
    myFixture.file.putUserData(CreateClassInPackageInModuleFix.IS_INTERFACE, isInterface)
    myFixture.file.putUserData(CreateClassInPackageInModuleFix.ROOT_DIR, readAction { myFixture.file.containingDirectory })
    myFixture.file.putUserData(CreateClassInPackageInModuleFix.NAME, name)

    val action = myFixture.findSingleIntention("Create a class in '$packageName'")
    myFixture.launchAction(action)
    myFixture.checkHighlighting(false, false, false) // no error

    readAction {
      val psiClass = myFixture.findClass("$packageName.$name")
      assertEquals(isInterface, psiClass.isInterface)

      val ownerModule = JavaModuleGraphUtil.findDescriptorByElement(psiClass)
      assertEquals(moduleInfo.moduleDeclaration!!, ownerModule)
    }
  }
}
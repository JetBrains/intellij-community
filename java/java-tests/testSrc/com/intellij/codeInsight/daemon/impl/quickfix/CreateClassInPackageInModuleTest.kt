// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.intention.impl.CreateClassInPackageInModuleFix
import com.intellij.codeInspection.java19modules.JavaModuleDefinitionInspection
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class CreateClassInPackageInModuleTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(JavaModuleDefinitionInspection())
  }

  fun testExportsMissingDir(): Unit = doTestMissingDir("exports")
  fun testOpensMissingDir(): Unit = doTestMissingDir("opens")

  fun testExportsEmptyDir(): Unit = doTestEmptyDir("exports")
  fun testOpensEmptyDir(): Unit = doTestEmptyDir("opens")

  fun testExportsInterface(): Unit = doTestInterface("exports")
  fun testOpensInterface(): Unit = doTestInterface("opens")

  private fun doTestMissingDir(keyword: String) {
    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { $keyword foo.bar.<caret>missing; }") as PsiJavaFile
    doAction("foo.bar.missing", moduleInfo)
  }

  private fun doTestEmptyDir(keyword: String) {
    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { $keyword foo.bar.<caret>empty; }") as PsiJavaFile
    val dir = myFixture.tempDirFixture.findOrCreateDir("foo.bar.empty")
    assertNotNull(dir)
    doAction("foo.bar.empty", moduleInfo)
  }

  private fun doTestInterface(keyword: String) {
    val moduleInfo = myFixture.configureByText("module-info.java", "module foo.bar { $keyword foo.bar.<caret>missing; }") as PsiJavaFile
    doAction("foo.bar.missing", moduleInfo, isInterface = true, name = "MyInterface")
  }

  private fun doAction(packageName: String, moduleInfo: PsiJavaFile,
                       isInterface: Boolean = false, name: String = "MyClass") {
    file.putUserData(CreateClassInPackageInModuleFix.IS_INTERFACE, isInterface)
    file.putUserData(CreateClassInPackageInModuleFix.ROOT_DIR, file.containingDirectory)
    file.putUserData(CreateClassInPackageInModuleFix.NAME, name)

    val action = myFixture.findSingleIntention("Create a class in '$packageName'")
    myFixture.launchAction(action)
    myFixture.checkHighlighting(false, false, false) // no error

    val psiClass = myFixture.findClass("$packageName.$name")
    assertEquals(isInterface, psiClass.isInterface)

    val ownerModule = JavaModuleGraphUtil.findDescriptorByElement(psiClass)
    assertEquals(moduleInfo.moduleDeclaration!!, ownerModule)
  }
}
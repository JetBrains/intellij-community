// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.impl.file.impl.JavaFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenameProcessor
import org.assertj.core.api.Assertions.assertThat

class ModuleRenameTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  fun testRename() {
    val file = addFile("module-info.java", "module M2 { }", M2)
    myFixture.configureByText("module-info.java", "module M { requires M2; }")
    RenameProcessor(project, findModule("M2"), "M2.bis", false, false).run()
    myFixture.checkResult("module M { requires M2.bis; }")
    assertEquals("module M2.bis { }", fileText(file))
  }

  fun testRenameIndirectReference() {
    val file = addFile("module-info.java", "module M2 { exports pkg.m2 to M.main; opens pkg.m2.impl to M.main; }", M2)
    myFixture.configureByText("module-info.java", "module M.main { requires M2; }")
    RenameProcessor(project, findModule("M.main"), "M.other", false, false).run()
    myFixture.checkResult("module M.other { requires M2; }")
    assertEquals("module M2 { exports pkg.m2 to M.other; opens pkg.m2.impl to M.other; }", fileText(file))
  }

  private fun findModule(name: String): PsiJavaModule {
    val modules = JavaFileManager.getInstance(project).findModules(name, GlobalSearchScope.projectScope(project))
    assertThat(modules).hasSize(1)
    return modules.first()
  }

  private fun fileText(file: VirtualFile) = myFixture.psiManager.findFile(file)!!.text
}
/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiJavaModule
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.MultiModuleJava9ProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

class ModuleHighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = MultiModuleJava9ProjectDescriptor

  fun testWrongFileName() {
    myFixture.configureByText("M.java", """/* ... */ <error descr="Module declaration should be in a file named 'module-info.java'">module M</error> { }""")
    myFixture.checkHighlighting()
  }

  fun testFileDuplicate() {
    additionalFile("""module M.bis { }""")
    doTest("""<error descr="'module-info.java' already exists in the module">module M</error> { }""")
  }

  fun testWrongFileLocation() {
    additionalFile("""<warning descr="Module declaration should be located in a module's source root">module M</warning> { }""")
    myFixture.checkHighlighting()
  }

  fun testDuplicateRequires() = doTest("""module M { requires M2; <error descr="Duplicate requires: M2">requires M2;</error> }""", true)

  fun testUnresolvedModule() = doTest("""module M { requires <error descr="Module not found: M.missing">M.missing</error>; }""")

  fun testSelfDependence() = doTest("""module M { requires <error descr="Cyclic dependence: M">M</error>; }""")

  fun testCyclicDependence() = doTest("""module M1 { requires <error descr="Cyclic dependence: M1, M2">M2</error>; }""", true)

  //<editor-fold desc="Helpers.">
  private fun additionalFile(text: String) = myFixture.configureFromExistingVirtualFile(runWriteAction {
    val file = LightPlatformTestCase.getSourceRoot().createChildDirectory(this, "pkg").createChildData(this, "module-info.java")
    VfsUtil.saveText(file, text)
    file
  })

  private fun doTest(text: String, filter: Boolean = false) {
    myFixture.configureByText("module-info.java", text)
    if (filter) {
      (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter { it.name != PsiJavaModule.MODULE_INFO_FILE }
    }
    myFixture.checkHighlighting()
  }
  //</editor-fold>
}
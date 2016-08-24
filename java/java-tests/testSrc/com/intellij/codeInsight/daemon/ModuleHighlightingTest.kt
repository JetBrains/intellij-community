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
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiJavaModule
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

class ModuleHighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

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

  fun testDuplicateRequires() {
    doTest("""module M { requires M2; <error descr="Duplicate requires: M2">requires M2;</error> }""", true)
  }

  fun testUnresolvedModule() {
    doTest("""module M { requires <error descr="Module not found: M.missing">M.missing</error>; }""")
  }

  fun testSelfDependence() {
    doTest("""module M { requires <error descr="Cyclic dependence: M">M</error>; }""")
  }

  fun testCyclicDependence() {
    doTest("""module M1 { requires <error descr="Cyclic dependence: M1, M2">M2</error>; }""", true)
  }

  //<editor-fold desc="Helpers.">
  companion object {
    private val DESCRIPTOR = object : DefaultLightProjectDescriptor() {
      override fun getSdk() = IdeaTestUtil.getMockJdk18()

      override fun setUpProject(project: Project, handler: SetupHandler) {
        super.setUpProject(project, handler)
        runWriteAction {
          val m2 = createModule(project, FileUtil.join(FileUtil.getTempDirectory(), "light_idea_test_case_m2.iml"))
          val src2 = createSourceRoot(m2, "src2")
          createContentEntry(m2, src2)

          VfsUtil.saveText(src2.createChildData(this, "module-info.java"), "module M2 { requires M1; }")
        }
      }

      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_9
      }
    }
  }

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
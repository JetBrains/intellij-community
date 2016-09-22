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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.file.impl.JavaFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import org.junit.Test

class ModuleRenameTest  : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = MultiModuleJava9ProjectDescriptor

  override fun setUp() {
    super.setUp()
    addFile("module-info.java", "module M2 { }", M2)
  }

  override fun tearDown() {
    MultiModuleJava9ProjectDescriptor.cleanupSourceRoots()
    super.tearDown()
  }

  @Test fun testRename() {
    myFixture.configureByText("module-info.java", "module M { requires M2; }")
    val module = JavaFileManager.SERVICE.getInstance(project).findModules("M2", GlobalSearchScope.allScope(project)).first()
    runWriteAction {
      RenameProcessor(project, module, "M2.bis", false, false).run()
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    myFixture.checkResult("module M { requires M2.bis; }")
  }

  private fun addFile(path: String, text: String, module: ModuleDescriptor = MAIN) = VfsTestUtil.createFile(module.root(), path, text)
}
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
package com.intellij.codeInsight.completion

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.MultiModuleJava9ProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import org.assertj.core.api.Assertions.assertThat

class ModuleCompletionTest : LightFixtureCompletionTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = MultiModuleJava9ProjectDescriptor

  fun testFileHeader() = complete("<caret>", "module <caret>")
  fun testStatements1() = variants("module M { <caret> }", "requires", "exports", "uses", "provides")
  fun testStatements2() = complete("module M { requires X; ex<caret> }", "module M { requires X; exports <caret> }")
  fun testModuleRef() = complete("module M { requires M<caret> }", "module M { requires M2;<caret> }")

  fun testExports() {
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/C.java", "package pkg.main;\nclass C { }")
    addFile("pkg/other/C.groovy", "package pkg.other\nclass C { }")
    variants("module M { exports pkg.<caret> }", "pkg.main", "pkg.other")
  }

  //<editor-fold desc="Helpers.">
  private fun addFile(path: String, text: String) = VfsTestUtil.createFile(LightPlatformTestCase.getSourceRoot(), path, text)

  private fun complete(text: String, expected: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.completeBasic()
    myFixture.checkResult(expected)
  }

  private fun variants(text: String, vararg variants: String) {
    myFixture.configureByText("module-info.java", text)
    val result = myFixture.completeBasic()?.map { it.lookupString }
    assertThat(result).containsExactlyInAnyOrder(*variants)
  }
  //</editor-fold>
}
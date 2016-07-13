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
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class ModuleHighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  fun testWrongFileName() {
    myFixture.configureByText("M.java", """/* ... */ <error descr="Module declaration should be in a file named 'module-info.java'">module M</error> { }""")
    myFixture.checkHighlighting()
  }

  fun testFileDuplicate() {
    myFixture.configureFromExistingVirtualFile(runWriteAction {
      val file = LightPlatformTestCase.getSourceRoot().createChildDirectory(this, "pkg").createChildData(this, "module-info.java")
      VfsUtil.saveText(file, "module M { }")
      file
    })
    myFixture.configureByText("module-info.java", """<error descr="Multiple module declarations">module M</error> { }""")
    myFixture.checkHighlighting()
  }

  fun testWrongFileLocation() {
    myFixture.configureFromExistingVirtualFile(runWriteAction {
      val file = LightPlatformTestCase.getSourceRoot().createChildDirectory(this, "pkg").createChildData(this, "module-info.java")
      VfsUtil.saveText(file, """<warning descr="Module declaration should be located in a module's source root">module M</warning> { }""")
      file
    })
    myFixture.checkHighlighting()
  }
}
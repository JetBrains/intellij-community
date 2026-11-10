/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.ide.actions

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.JavaTemplateUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class CreateClassActionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testLiveTemplate(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val dir = myFixture.tempDirFixture.findOrCreateDir("foo")
    val psiDirectory = readAction { PsiManager.getInstance(myFixture.project).findDirectory(dir) }
    val template = FileTemplateManager.getInstance(myFixture.project).addTemplate("fooBar", "java")
    template.text = "public class \${NAME} { #[[\$Title$]]# } "
    template.isLiveTemplateEnabled = true
    val clazz = withContext(Dispatchers.EDT) { JavaDirectoryService.getInstance().createClass(psiDirectory!!, "Bar", template.name) }
    readAction { assertEquals("public class Bar {\n    Title\n}", clazz.text) }
  }

  @Test
  fun testImplicitClassLiveTemplate(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val dir = myFixture.tempDirFixture.findOrCreateDir("")
    val psiDirectory = readAction { PsiManager.getInstance(myFixture.project).findDirectory(dir) }
    val expectedFileName = "Bar"
    val clazz = withContext(Dispatchers.EDT) { JavaDirectoryService.getInstance().createClass(psiDirectory!!, expectedFileName, JavaTemplateUtil.INTERNAL_SIMPLE_SOURCE_FILE) }
    readAction {
      assertEquals("""
        void main() {
            
        }
      """.trimIndent(), clazz.text)
      val fileName = clazz.containingFile.name
      assertEquals("$expectedFileName.java", fileName)
    }
  }
}
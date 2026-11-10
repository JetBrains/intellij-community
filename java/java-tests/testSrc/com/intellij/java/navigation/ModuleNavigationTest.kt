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
package com.intellij.java.navigation

import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class ModuleNavigationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testNavigation(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = myFixture.configureByText("module-info.java", "module my.mod.name { }")
    withContext(Dispatchers.EDT) {
      (file as PsiJavaFile).moduleDeclaration!!.navigate(true)
      assertThat(myFixture.editor.caretModel.offset).isEqualTo(file.text.indexOf("my.mod.name"))
    }
  }

  @Test
  fun testGoToSymbol(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = myFixture.configureByText("module-info.java", "module my.mod.name { }")
    readAction {
      val items = GotoSymbolModel2(myFixture.project, myFixture.testRootDisposable).getElementsByName("my.mod.name", false, "my.mod")
      assertThat(items).containsExactly((file as PsiJavaFile).moduleDeclaration!!)
    }
  }
}
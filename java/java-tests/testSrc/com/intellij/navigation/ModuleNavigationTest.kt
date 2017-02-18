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
package com.intellij.navigation

import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat

class ModuleNavigationTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  fun testNavigation() {
    val file = myFixture.configureByText("module-info.java", "module my.mod.name { }")
    (file as PsiJavaFile).moduleDeclaration!!.navigate(true)
    assertThat(myFixture.editor.caretModel.offset).isEqualTo(file.text.indexOf("my.mod.name"))
  }

  fun testGoToSymbol() {
    val file = myFixture.configureByText("module-info.java", "module my.mod.name { }")
    val items = GotoSymbolModel2(myFixture.project).getElementsByName("my.mod.name", false, "my.mod")
    assertThat(items).containsExactly((file as PsiJavaFile).moduleDeclaration!!)
  }
}
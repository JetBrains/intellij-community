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
package com.intellij.java.codeInsight.javadoc

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.loadElement

class LightJavadocGeneratorTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  fun testPlainModule() = doTestModule("module M.N { }", """<div class='definition'><pre>module <b>M.N</b></pre></div>""".trimIndent())

  fun testDocumentedModule() = doTestModule("/** One humble module. */\n@Deprecated\nmodule M.N { }", """<div class='definition'><pre>@<a href="psi_element://java.lang.Deprecated"><code>Deprecated</code></a> 
module <b>M.N</b></pre></div><div class='content'> One humble module. </div><table class='sections'><p></table>""".trimIndent())

  private fun doTestModule(text: String, expected: String) {
    val file = myFixture.configureByText("module-info.java", text)
    val module = (file as PsiJavaFile).moduleDeclaration!!
    val docInfo = JavaDocInfoGeneratorFactory.create(project, module).generateDocInfo(null)!!.replace("&nbsp;", " ")
    assertThat(docInfo).isEqualTo(expected)
  }
}
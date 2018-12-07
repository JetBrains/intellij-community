// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

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
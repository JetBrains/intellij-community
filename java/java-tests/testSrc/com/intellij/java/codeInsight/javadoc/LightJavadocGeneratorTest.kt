// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class LightJavadocGeneratorTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_9

  fun testPlainModule() = doTestModule(
    "module M.N { }",
    "<div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">module </span><span style=\"color:#000000;\">M.N</span></pre></div>")

  fun testDocumentedModule() = doTestModule(
    "/** One humble module. */\n@Deprecated\nmodule M.N { }",
    "<div class='definition'><pre><span style=\"color:#808000;\">@</span><a href=\"psi_element://java.lang.Deprecated\"><code><span style=\"color:#808000;\">Deprecated</span></code></a> \n" +
    "<span style=\"color:#000080;font-weight:bold;\">module </span><span style=\"color:#000000;\">M.N</span></pre></div><div class='content'> One humble module. </div>")

  fun testRootedClassLink() = doTestLink("{@docRoot}/java/lang/Character.html#unicode", "psi_element://java.lang.Character###unicode")
  fun testRootedPackageLink() = doTestLink("{@docRoot}/java/util/package-summary.html", "psi_element://java.util")
  fun testRootedFileLink() = doTestLink("{@docRoot}/java/lang/doc-files/ValueBased.html", "../../java/lang/doc-files/ValueBased.html")
  fun testRootedExtLink() = doTestLink("{@docRoot}/../techNotes/guides/index.html", "../../../techNotes/guides/index.html")

  fun testRelativeClassLink() = doTestLink("../../java/lang/Character.html#unicode", "psi_element://java.lang.Character###unicode")
  fun testRelativePackageLink() = doTestLink("../../java/util/package-summary.html", "psi_element://java.util")
  fun testRelativeFileLink() = doTestLink("../../java/lang/doc-files/ValueBased.html", "../../java/lang/doc-files/ValueBased.html")
  fun testRelativeExtLink() = doTestLink("../../../techNotes/guides/index.html", "../../../techNotes/guides/index.html")

  //<editor-fold desc="Helpers.">
  private fun doTestModule(text: String, expected: String) {
    val file = myFixture.configureByText("module-info.java", text) as PsiJavaFile
    assertThat(generateDoc(file.moduleDeclaration!!)).isEqualTo(expected)
  }

  private fun doTestLink(link: String, expected: String) {
    doTestClass(
      "package a.b;\n/** A <a href=\"${link}\">link</a>. */\ninterface I { }",
      "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Package\"> <code><span style=\"color:#000000;\">a.b</span></code></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">interface</span> <span style=\"color:#000000;\">I</span></pre></div><div class='content'> A <a href=\"$expected\">link</a>. </div>")
  }

  private fun doTestClass(text: String, expected: String) {
    val file = myFixture.configureByText("a.java", text) as PsiJavaFile
    assertThat(generateDoc(file.classes[0])).isEqualTo(expected)
  }

  private fun generateDoc(element: PsiElement): String? =
    JavaDocInfoGeneratorFactory.create(project, element).generateDocInfo(null)
      ?.replace("&nbsp;", " ")
      ?.substringBeforeLast("<table class='sections'><p></table>")
  //</editor-fold>
}

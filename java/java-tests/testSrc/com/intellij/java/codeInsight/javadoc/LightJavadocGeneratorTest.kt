// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.net.URL

@TestApplication
class LightJavadocGeneratorTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testPlainModule(): Unit = doTestModule(
    "module M.N { }",
    "<div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">module </span><span style=\"color:#000000;\">M.N</span></pre></div>")

  @Test
  fun testDocumentedModule(): Unit = doTestModule(
    "/** One humble module. */\n@Deprecated\nmodule M.N { }",
    "<div class='definition'><pre><span style=\"color:#808000;\">@</span><a href=\"psi_element://java.lang.Deprecated\"><code><span style=\"color:#808000;\">Deprecated</span></code></a> \n" +
    "<span style=\"color:#000080;font-weight:bold;\">module </span><span style=\"color:#000000;\">M.N</span></pre></div><div class='content'> One humble module. </div>")

  @Test
  fun testRootedClassLink(): Unit = doTestLink("{@docRoot}/java/lang/Character.html#unicode", "psi_element://java.lang.Character###unicode")
  @Test
  fun testRootedPackageLink(): Unit = doTestLink("{@docRoot}/java/util/package-summary.html", "psi_element://java.util")
  @Test
  fun testRootedFileLink(): Unit = doTestLink("{@docRoot}/java/lang/doc-files/ValueBased.html", "../../java/lang/doc-files/ValueBased.html")
  @Test
  fun testRootedExtLink(): Unit = doTestLink("{@docRoot}/../techNotes/guides/index.html", "../../../techNotes/guides/index.html")

  @Test
  fun testRelativeClassLink(): Unit = doTestLink("../../java/lang/Character.html#unicode", "psi_element://java.lang.Character###unicode")
  @Test
  fun testRelativePackageLink(): Unit = doTestLink("../../java/util/package-summary.html", "psi_element://java.util")
  @Test
  fun testRelativeFileLink(): Unit = doTestLink("../../java/lang/doc-files/ValueBased.html", "../../java/lang/doc-files/ValueBased.html")
  @Test
  fun testRelativeExtLink(): Unit = doTestLink("../../../techNotes/guides/index.html", "../../../techNotes/guides/index.html")

  //<editor-fold desc="Helpers.">
  private fun doTestModule(text: String, expected: String): Unit = timeoutRunBlocking {
    val file = myFixture.configureByText("module-info.java", text) as PsiJavaFile
    assertThat(readAction { generateDoc(file.moduleDeclaration!!) }).isEqualTo(prologue(baseUrl(file)) + expected)
  }

  /**
   * Checks the `href` of the link in the documentation.
   * An [expected] value that is not a `psi_element://` link is a file link, relative to the file of the class.
   */
  private fun doTestLink(link: String, expected: String) {
    doTestClass("package a.b;\n/** A <a href=\"${link}\">link</a>. */\ninterface I { }") { baseUrl ->
      val href = if (expected.startsWith("psi_element://")) expected else baseUrl.toURI().resolve(expected).toString()
      "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Package\"> <a href=\"psi_element://a.b\"><code><span style=\"color:#000000;\">a.b</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">interface</span> <span style=\"color:#000000;\">I</span></pre></div><div class='content'> A <a href=\"$href\">link</a>. </div>"
    }
  }

  private fun doTestClass(text: String, expected: (baseUrl: URL) -> String): Unit = timeoutRunBlocking {
    val file = myFixture.configureByText("a.java", text) as PsiJavaFile
    val baseUrl = baseUrl(file)
    assertThat(readAction { generateDoc(file.classes[0]) }).isEqualTo(prologue(baseUrl) + expected(baseUrl))
  }

  /**
   * Returns the URL of [file]. [com.intellij.codeInsight.javadoc.JavaDocInfoGenerator] writes it in the `<base href>` prologue
   * and resolves a relative file link against it.
   */
  private fun baseUrl(file: PsiFile): URL = VfsUtilCore.convertToURL(file.virtualFile.url)!!

  private fun prologue(baseUrl: URL): String = "<html><head><base href=\"$baseUrl\"></head><body>"

  private fun generateDoc(element: PsiElement): String? =
    JavaDocInfoGeneratorFactory.create(myFixture.project, element).generateDocInfo(null)
      ?.replace("&nbsp;", " ")
      ?.substringBeforeLast("<table class='sections'><p></table>")
  //</editor-fold>
}

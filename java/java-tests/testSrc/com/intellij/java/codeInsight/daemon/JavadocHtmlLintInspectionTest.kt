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
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInspection.javaDoc.JavadocHtmlLintInspection
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.PathUtil

class JavadocHtmlLintInspectionTest : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    val javaHome = System.getProperty("java.home")
    val jdkHome = if (javaHome.endsWith("jre")) PathUtil.getParentPath(javaHome) else javaHome
    VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, jdkHome)

    val jdk = (JavaSdk.getInstance() as JavaSdkImpl).createMockJdk("java version \"1.8.0\"", System.getProperty("java.home"), true)
    ModuleRootModificationUtil.setModuleSdk(myModule, jdk);
  }

  fun testNoComment() = doTest("class C { }")
  fun testEmptyComment() = doTest("/** */\nclass C { }")

  fun testCommonErrors() = doTest("""
    package pkg;
    /**
     * <ul><error descr="Tag not allowed here: <p>"><p></error>Paragraph inside a list</p></ul>
     *
     * Empty paragraph: <error descr="Self-closing element not allowed"><p/></error>
     *
     * Line break: <error descr="Self-closing element not allowed"><br/></error>
     * Another one: <br><error descr="Invalid end tag: </br>"></br></error>
     * And the last one: <br> <error descr="Invalid end tag: </br>"></br></error>
     *
     * Missing open tag: <error descr="Unexpected end tag: </i>"></i></error>
     *
     * Unescaped angle brackets for generics: List<error descr="Unknown tag: String"><String></error>
     * (closing it here to avoid further confusion: <error descr="Unknown tag: String"></String></error>)
     * Correct: {@code List<String>}
     *
     * Unknown attribute: <br <error descr="Unknown attribute: a">a</error>="">
     *
     * <p <error descr="Invalid name for anchor: \"1\"">id</error>="1" <error descr="Repeated attribute: id">id</error>="2">Some repeated attributes</p>
     *
     * <p>Empty ref: <a <error descr="Attribute lacks value">href</error>="">link</a></p>
     *
     * <error descr="Header used out of sequence: <H4>"><h4></error>Incorrect header</h4>
     *
     * Unknown entity: <error descr="Invalid entity &wtf;">&wtf;</error>
     *
     * @see bad_link should report no error
     */
    class C { }""".trimIndent())

  fun testPackageInfo() = doTest("""
    /**
     * Another self-closed paragraph: <error descr="Self-closing element not allowed"><p/></error>
     */
    package pkg;""".trimIndent(), "package-info.java")

  private fun doTest(text: String, name: String? = null) {
    myFixture.enableInspections(JavadocHtmlLintInspection())
    myFixture.configureByText(name ?: "${getTestName(false)}.java", text)
    myFixture.checkHighlighting(true, false, false)
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.codeInspection.javaDoc.JavadocHtmlLintInspection
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.lang.JavaVersion
import java.io.File

private val DESCRIPTOR = object : DefaultLightProjectDescriptor() {
  override fun getSdk(): Sdk? {
    val jreHome = File(System.getProperty("java.home"))
    val jdkHome = if (jreHome.name == "jre") jreHome.parentFile else jreHome
    return IdeaTestUtil.createMockJdk("java version \"{${JavaVersion.current()}}\"", jdkHome.path)
  }
}

class JavadocHtmlLintInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

  fun testNoComment() = doTest("class C { }")

  fun testEmptyComment() = doTest("/** */\nclass C { }")

  @ReviseWhenPortedToJDK("17")
  @Suppress("GrazieInspection")
  fun testCommonErrorsJdk11() {
    if (JavaSdkUtil.isJdkAtLeast(DESCRIPTOR.sdk, JavaSdkVersion.JDK_17)) return
    doTest("""
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
       * <p <error descr="Invalid name for anchor: \"1\"">id</error>="1" <error descr="Repeated attribute: id">id</error>="A">Some repeated attributes</p>
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
  }
  
 @Suppress("GrazieInspection")
  fun testCommonErrors() {
   if (!JavaSdkUtil.isJdkAtLeast(DESCRIPTOR.sdk, JavaSdkVersion.JDK_17)) return
   doTest("""
      package pkg;
      /**
       * <ul><error descr="Tag not allowed here: <p>"><p></error>Paragraph inside a list</p></ul>
       *
       * Empty paragraph: <error descr="Self-closing element not allowed"><p/></error>
       *
       * Line break: <br/>
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
       * <p id="1" <error descr="Repeated attribute: id">id</error>="A">Some repeated attributes</p>
       *
       * <p>Empty ref: <a <error descr="Attribute lacks value">href</error>="">link</a></p>
       *
       * <error descr="Heading used out of sequence: <H4>, compared to implicit preceding heading: <H1>"><h4></error>Incorrect header</h4>
       *
       * Unknown entity: <error descr="Invalid entity &wtf;">&wtf;</error>
       *
       * @see bad_link should report no error
       */
      class C { }""".trimIndent())
  }

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
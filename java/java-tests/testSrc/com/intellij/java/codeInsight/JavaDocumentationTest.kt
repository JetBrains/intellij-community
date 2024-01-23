// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.javadoc.DocumentationDelegateProvider
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import java.util.concurrent.Callable

class JavaDocumentationTest : LightJavaCodeInsightFixtureTestCase() {
  fun testConstructorJavadoc() {
    configure("""\
      class Foo { Foo() {} Foo(int param) {} }

      class Foo2 {{
        new Foo<caret>
      }}""".trimIndent())

    val originalElement = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    val element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
    val doc = JavaDocumentationProvider().generateDoc(element, originalElement)

    val expected =
      "<html>" +
      "Candidates for new <b>Foo</b>() are:<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo()\">Foo()</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo(int)\">Foo(int param)</a><br>" +
      "</html>"

    assertEquals(expected, doc)
  }

  fun testConstructorJavadoc2() {
    configure("""
      class Foo { Foo() {} Foo(int param) {} }

      class Foo2 {{
        new Foo(<caret>)
      }}""".trimIndent())

    val elementAt = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    val exprList = PsiTreeUtil.getParentOfType(elementAt, PsiExpressionList::class.java)
    val doc = JavaDocumentationProvider().generateDoc(exprList, elementAt)

    val expected =
      "<html>" +
      "Candidates for new <b>Foo</b>() are:<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo()\">Foo()</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo(int)\">Foo(int param)</a><br>" +
      "</html>"

    assertEquals(expected, doc)
  }

  fun testMethodDocWhenInArgList() {
    configure("""
      class Foo { void doFoo() {} }

      class Foo2 {{
        new Foo().doFoo(<caret>)
      }}""".trimIndent())

    val exprList = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset),
                                               PsiExpressionList::class.java)
    val doc = JavaDocumentationProvider().generateDoc(exprList, null)

    val expected =
      "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://Foo\"><code><span style=\"color:#000000;\">Foo</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">doFoo</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><table class='sections'><p></table>"

    assertEquals(expected, doc)
  }

  fun testPatternDoc() {
    configure("""
      /**
       *  @param i my parameter
       */ 
      record Rec(int i, String s) {
        void foo(Object o) {
          switch (o) {
            case Rec(int patternName1, String patternName2) -> {
              <caret>patternName1            
            }      
          }
        }
      }""".trimIndent())

    val ref = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset),
                                               PsiReferenceExpression::class.java)!!
    val doc = JavaDocumentationProvider().generateDoc(ref.resolve(), null)

    val expected =
      "<div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">int</span> <span style=\"color:#000000;\">patternName1</span></pre></div><div class='content'>my parameter</div>"

    assertEquals(expected, doc)
  }

  fun testRecordComponent() {
    configure("""
      /**
       * @param foo doc for foo
       * @param bar doc for bar
       */
      public record Rec(int <caret> foo, int bar) {
      }
      """.trimIndent())

    val recordComponent = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset),
                                               PsiRecordComponent::class.java)!!
    val doc = JavaDocumentationProvider().generateDoc(recordComponent, null)

    val expected =
      "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://Rec\"><code><span style=\"color:#000000;\">Rec</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">int</span> <span style=\"color:#000000;\">foo</span></pre></div><div class='content'>doc for foo  </div>"

    assertEquals(expected, doc)
  }

  fun testGenericMethod() {
    doTestCtrlHoverDoc(
      """\
      class Bar<T> { java.util.List<T> foo(T param); }

      class Foo {{
        new Bar<String>().f<caret>oo();
      }}""",
      """<span style="color:#000000;"><a href="psi_element://Bar">Bar</a></span><br/> <span style="color:#000000;"><a href="psi_element://java.util.List">List</a></span><span style="">&lt;</span><span style="color:#000000;">String</span><span style="">&gt;</span> <span style="color:#000000;">foo</span><span style="">(</span><span style="color:#000000;">String</span> <span style="color:#000000;">param</span><span style="">)</span>"""
    )
  }

  fun testGenericField() {
    doTestCtrlHoverDoc(
      """\
      class Bar<T> { T field; }

      class Foo {{
        new Bar<Integer>().fi<caret>eld
      }}""",

      """<span style="color:#000000;"><a href="psi_element://Bar">Bar</a></span><br/> <span style="color:#000000;">Integer</span> <span style="color:#660e7a;font-weight:bold;">field</span>"""
    )
  }

  fun testMethodInAnonymousClass() {
    doTestCtrlHoverDoc(
      """\
      class Foo {{
        new Runnable() {
          @Override
          public void run() {
            <caret>m();
          }

          private void m() {}
        }.run();
      }}""",
      "<span style=\"color:#000080;font-weight:bold;\">private</span> <span style=\"color:#000080;font-weight:bold;\">void</span> <span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span>"
    )
  }


  fun testAnnotationInCtrlHoverDoc() {
    doTestCtrlHoverDoc(
      """\
      class Foo {
          {<caret>m();}
          @Anno
          private void m() {}
      }
      @java.lang.annotation.Documented
      @interface Anno {} """,
      """<span style="color:#000000;"><a href="psi_element://Foo">Foo</a></span><br/> <span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Anno">Anno</a></span>&nbsp;<br/><span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">void</span> <span style="color:#000000;">m</span><span style="">(</span><span style="">)</span>"""
    )
  }

  fun testInnerClass() {
    doTestCtrlHoverDoc(
      """\
      class C {
        Outer.Inner field;
        
        void m() {
          <caret>field.hashCode();
        }
      }
      class Outer {
        class Inner {}
      }""",
      """<span style="color:#000000;"><a href="psi_element://C">C</a></span><br/> <span style="color:#000000;"><a href="psi_element://Outer.Inner">Outer.Inner</a></span> <span style="color:#660e7a;font-weight:bold;">field</span>"""
    )
  }

  fun testAsterisksFiltering() {
    configure("""
      class C {
        /**
         * For example, {@link String#String(byte[],
         * int, int,
         * String)}.
         */
        public void <caret>m() { }
      }
    """.trimIndent())

    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)

    val expected = "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://C\"><code><span style=\"color:#000000;\">C</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span>&nbsp;<span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><div class='content'>\n  For example, <a href=\"psi_element://java.lang.String#String(byte[], int, int, java.lang.String)\"><code><span style=\"color:#0000ff;\">String</span><span style=\"\">.</span><span style=\"color:#0000ff;\">String</span><span style=\"\">(</span><span style=\"color:#000080;font-weight:bold;\">byte</span><span style=\"\">[],&#32;</span><span style=\"color:#000080;font-weight:bold;\">int</span><span style=\"\">,&#32;</span><span style=\"color:#000080;font-weight:bold;\">int</span><span style=\"\">,&#32;String)</span></code></a>.\n   </div><table class='sections'></table>"
    TestCase.assertEquals(expected, doc)
  }

  fun testInlineTagSpacing() {
    configure("""
      class C {
        /** Visit the "{@code /login}" URL. */
        public void <caret>m() { }
      }
    """.trimIndent())

    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)

    val expected = "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://C\"><code><span style=\"color:#000000;\">C</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span>&nbsp;<span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><div class='content'> Visit the \"<code><span style=\"\">/login</span></code>\" URL. </div><table class='sections'></table>"
    TestCase.assertEquals(expected, doc)
  }

  fun testInlineReturnJava16() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_16) {
      configure("""
      class C {
        /** {@return smth} */
        public String <caret>m() { }
      }
    """.trimIndent())
      val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
      val doc = JavaDocumentationProvider().generateRenderedDoc(method!!.docComment!!)
      val expected = "<div class='content'> Returns smth. </div><table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><p> smth</td></table>"
      TestCase.assertEquals(expected, doc)
    }
  }


  fun testMethodToMethodDelegate() {
    val provider: DocumentationDelegateProvider = object : DocumentationDelegateProvider() {
      override fun computeDocumentationDelegate(member: PsiMember): PsiDocCommentOwner? {
        if (member is PsiMethod && member.name == "foo") {
          return JavaPsiFacade.getInstance(project).findClass("Foo", member.getResolveScope())!!.findMethodBySignature(member, false)
        }
        return null
      }
    }
    DocumentationDelegateProvider.EP_NAME.point.registerExtension(provider, myFixture.testRootDisposable)
    configure("""
      class Foo {
        /**
        * Some doc
        */
        void foo() {}
      }

      class Bar {
        void fo<caret>o() {}
      }
    """.trimIndent())

    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)
    val expected = "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://Bar\"><code><span style=\"color:#000000;\">Bar</span></code></a></div><div class=\'definition\'><pre><span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">foo</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><table class=\'sections\'><p><tr><td valign=\'top\' class=\'section\'><p>From class:</td><td valign=\'top\'><p><a href=\"psi_element://Foo\"><code><span style=\"color:#000000;\">Foo</span></code></a><br>\n  Some doc\n  </td></table>"

    TestCase.assertEquals(expected, doc)
  }

  fun `test at method name with overloads`() {
    val input = """
      class Foo {
        void foo(String s) {
          s.region<caret>Matches()
        } 
      }
    """.trimIndent()

    val actual = JavaExternalDocumentationTest.getDocumentationText(myFixture.project, input)

    val expected = "<html><div class='content-only'>Candidates for method call <b>s.regionMatches()</b> are:<br>" +
                   "<br>" +
                   "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)\">boolean regionMatches(int, String, int, int)</a><br>" +
                   "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(boolean, int, java.lang.String, int, int)\">boolean regionMatches(boolean, int, String, int, int)</a><br>" +
                   "</div>"

    TestCase.assertEquals(expected, actual)
  }

  fun `test navigation updates decoration`() {
    val input = """
      class Foo {
        void foo(String s) {
          s.region<caret>Matches()
        } 
      }
    """.trimIndent()

    val documentationManager = DocumentationManager.getInstance(myFixture.project)
    JavaExternalDocumentationTest.getDocumentationText(myFixture.project, input) { component ->
      val expected = "<html><div class='content-only'>Candidates for method call <b>s.regionMatches()</b> are:<br>" +
                     "<br>" +
                     "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)\">boolean regionMatches(int, String, int, int)</a><br>" +
                     "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(boolean, int, java.lang.String, int, int)\">boolean regionMatches(boolean, int, String, int, int)</a><br>" +
                     "</div>"
      assertEquals(expected, component.decoratedText)

      documentationManager.navigateByLink(component, null, "psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)")
      try {
        JavaExternalDocumentationTest.waitTillDone(documentationManager.lastAction)
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }

      // Here we check that the covering module (SDK in this case) is rendered in decorated info
      assertTrue(
        component.decoratedText.contains("<div class=\"bottom\"><icon src=\"AllIcons.Nodes.PpLibFolder\"/>&nbsp;&lt; java 1.7 &gt;</div>"))
      return@getDocumentationText null
    }
  }


  private fun doTestCtrlHoverDoc(inputFile: String, expectedDoc: String) {
    configure(inputFile.trimIndent())
    val doc = ReadAction.nonBlocking (Callable { CtrlMouseHandler.getGoToDeclarationOrUsagesText (myFixture.editor) }).submit(AppExecutorUtil.getAppExecutorService()).get()
    assertEquals(expectedDoc, UIUtil.getHtmlBody(doc!!))
  }

  fun configure(text: String) {
    myFixture.configureByText("a.java", text)
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.javadoc.DocumentationDelegateProvider
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil

/**
 * @author peter
 */
class JavaDocumentationTest extends LightCodeInsightFixtureTestCase {
  void testConstructorDoc() {
    configure """\
      class Foo { Foo() {} Foo(int param) {} }

      class Foo2 {{
        new Foo<caret>
      }}""".stripIndent()

    def originalElement = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    def element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
    def doc = new JavaDocumentationProvider().generateDoc(element, originalElement)

    def expected =
      "<html>" +
      "Candidates for new <b>Foo</b>() are:<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo()\">Foo()</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo(int)\">Foo(int param)</a><br>" +
      "</html>"

    assert doc == expected
  }

  void testConstructorDoc2() {
    configure """\
      class Foo { Foo() {} Foo(int param) {} }

      class Foo2 {{
        new Foo(<caret>)
      }}""".stripIndent()

    def elementAt = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    def exprList = PsiTreeUtil.getParentOfType(elementAt, PsiExpressionList.class)
    def doc = new JavaDocumentationProvider().generateDoc(exprList, elementAt)

    def expected =
      "<html>" +
      "Candidates for new <b>Foo</b>() are:<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo()\">Foo()</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo(int)\">Foo(int param)</a><br>" +
      "</html>"

    assert doc == expected
  }

  void testMethodDocWhenInArgList() {
    configure """\
      class Foo { void doFoo() {} }

      class Foo2 {{
        new Foo().doFoo(<caret>)
      }}""".stripIndent()

    def exprList = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiExpressionList.class)
    def doc = new JavaDocumentationProvider().generateDoc(exprList, null)

    def expected =
      "<div class='definition'><pre><a href=\"psi_element://Foo\"><code>Foo</code></a><br>void&nbsp;<b>doFoo</b>()</pre></div><table class='sections'><p></table>"

    assert doc == expected
  }

  void testGenericMethod() {
    doTestCtrlHoverDoc("""\
      class Bar<T> { java.util.List<T> foo(T param); }

      class Foo {{
        new Bar<String>().f<caret>oo();
      }}""",
    "Bar<br/> <a href=\"psi_element://java.util.List\">List</a>&lt;String&gt; foo(String param)")
  }

  void testGenericField() {
    doTestCtrlHoverDoc("""\
      class Bar<T> { T field; }

      class Foo {{
        new Bar<Integer>().fi<caret>eld
      }}""",
      "Bar<br/> Integer field")
  }

  void testMethodInAnonymousClass() {
    doTestCtrlHoverDoc("""\
      class Foo {{
        new Runnable() {
          @Override
          public void run() {
            <caret>m();
          }

          private void m() {}
        }.run();
      }}""",
      "private void m()")
  }

  void testInnerClass() {
    doTestCtrlHoverDoc("""\
      class C {
        Outer.Inner field;
        
        void m() {
          <caret>field.hashCode();
        }
      }
      class Outer {
        class Inner {}
      }""",
      "<a href=\"psi_element://C\">C</a><br/> <a href=\"psi_element://Outer.Inner\">Outer.Inner</a> field")
  }

  void testAsterisksFiltering() {
    configure """\
      class C {
        /**
         * For example, {@link String#String(byte[],
         * int, int,
         * String)}.
         */
        public void <caret>m() { }
      }""".stripIndent()

    def method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod.class)
    def doc = new JavaDocumentationProvider().generateDoc(method, null)

    def expected =
      "<div class='definition'><pre><a href=\"psi_element://C\"><code>C</code></a><br>public&nbsp;void&nbsp;<b>m</b>()</pre></div><div class='content'>\n" +
      "     For example, <a href=\"psi_element://java.lang.String#String(byte[], int, int, java.lang.String)\"><code>String.String(byte[], int, int, String)</code></a>.\n" +
      "   <p></div><table class='sections'><p></table>"

    assert doc == expected
  }

  void testInlineTagSpacing() {
    configure """\
      class C {
        /** Visit the "{@code /login}" URL. */
        public void <caret>m() { }
      }""".stripIndent()

    def method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod.class)
    def doc = new JavaDocumentationProvider().generateDoc(method, null)

    def expected =
      "<div class='definition'><pre><a href=\"psi_element://C\"><code>C</code></a><br>public&nbsp;void&nbsp;<b>m</b>()</pre></div><div class='content'> Visit the \"<code>/login</code>\" URL. <p></div><table class='sections'><p></table>"

    assert doc == expected
  }

  void testMethodToMethodDelegate() {
    DocumentationDelegateProvider provider = {
      if (it instanceof PsiMethod && it.name == 'foo') {
        JavaPsiFacade.getInstance(project).findClass('Foo', it.resolveScope)?.findMethodBySignature(it, false)
      }
    }
    PlatformTestUtil.registerExtension DocumentationDelegateProvider.EP_NAME, provider, myFixture.testRootDisposable

    configure '''\
class Foo {
  /**
  * Some doc
  */
  void foo() {}
}

class Bar {
  void fo<caret>o() {}
}
'''
    def method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod.class)
    def doc = new JavaDocumentationProvider().generateDoc(method, null)

    String expected = "<div class='definition'><pre><a href=\"psi_element://Bar\"><code>Bar</code></a><br>void&nbsp;<b>foo</b>()</pre></div><table class='sections'><p><tr><td valign='top' class='section'><p>Description copied from class:</td><td valign='top'><p><a href=\"psi_element://Foo\"><code>Foo</code></a><br>\n" +
                      "    Some doc\n" +
                      "  </td></table>"

    assert doc == expected
  }

  void "test at method name with overloads"() {
    def input = """\
      class Foo {
        void foo(String s) {
          s.region<caret>Matches()
        } 
      }""".stripIndent()

    def actual = JavaExternalDocumentationTest.getDocumentationText(myFixture.project, input)

    def expected =
      "<html>Candidates for method call <b>s.regionMatches()</b> are:<br>" +
      "<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)\">boolean regionMatches(int, String, int, int)</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(boolean, int, java.lang.String, int, int)\">boolean regionMatches(boolean, int, String, int, int)</a><br>" +
      "</html>"

    assert actual == expected
  }

  private void configure(String text) {
    myFixture.configureByText 'a.java', text
  }

  void doTestCtrlHoverDoc(String inputFile, String expectedDoc) {
    configure inputFile.stripIndent()
    String doc = CtrlMouseHandler.getInfo(myFixture.editor, CtrlMouseHandler.BrowseMode.Declaration)
    assert UIUtil.getHtmlBody(doc) == expectedDoc
  }
}

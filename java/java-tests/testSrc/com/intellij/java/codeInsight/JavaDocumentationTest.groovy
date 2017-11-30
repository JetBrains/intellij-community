// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.javadoc.DocumentationDelegateProvider
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class JavaDocumentationTest extends LightCodeInsightFixtureTestCase {
  private static final String STYLE_BLOCK =
    "    <style type=\"text/css\">" +
    "        #error {" +
    "            background-color: #eeeeee;" +
    "            margin-bottom: 10px;" +
    "        }" +
    "        p {" +
    "            margin: 5px 0;" +
    "        }" +
    "    </style>"

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
      "<html><head>" + STYLE_BLOCK + "</head><body>" +
      "<small><b><a href=\"psi_element://Foo\"><code>Foo</code></a></b></small>" +
      "<PRE>void&nbsp;<b>doFoo</b>()</PRE>" +
      "</body></html>"

    assert doc == expected
  }

  void testGenericMethod() {
    doTestCtrlHoverDoc("""\
      class Bar<T> { java.util.List<T> foo(T param); }

      class Foo {{
        new Bar<String>().f<caret>oo();
      }}""",
    "Bar\n List&lt;String&gt; foo(String param)")
  }

  void testGenericField() {
    doTestCtrlHoverDoc("""\
      class Bar<T> { T field; }

      class Foo {{
        new Bar<Integer>().fi<caret>eld
      }}""",
      "Bar\n Integer field")
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
      "C\n Outer.Inner field")
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
      "<html><head>" + STYLE_BLOCK + "</head><body>" +
      "<small><b><a href=\"psi_element://C\"><code>C</code></a></b></small>" +
      "<PRE>public&nbsp;void&nbsp;<b>m</b>()</PRE>\n     " +
      "For example, <a href=\"psi_element://java.lang.String#String(byte[], int, int, java.lang.String)\">" +
      "<code>String.String(byte[], int, int, String)</code>" +
      "</a>.</body></html>"

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
      "<html><head>" + STYLE_BLOCK + "</head><body>" +
      "<small><b><a href=\"psi_element://C\"><code>C</code></a></b></small>" +
      "<PRE>public&nbsp;void&nbsp;<b>m</b>()</PRE>" +
      " Visit the \"<code>/login</code>\" URL." +
      "</body></html>"

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

    String expected = "<html><head>$STYLE_BLOCK</head><body>" +
                      "<small><b><a href=\"psi_element://Bar\"><code>Bar</code></a></b></small>" +
                      "<PRE>void&nbsp;<b>foo</b>()</PRE>" +
                      "<DD><DL><DT>" +
                      "<b>Description copied from class:</b>&nbsp;<a href=\"psi_element://Foo\"><code>Foo</code></a><br>" +
                      "\n    Some doc\n  " +
                      "</DD></DL></DD>" +
                      "</body></html>"

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

  void "test overload selected by completion"() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class C { void m() { System.getPro<caret> } }")
    def elements = myFixture.completeBasic()
    ((LookupImpl)myFixture.lookup).finishLookup(Lookup.NORMAL_SELECT_CHAR, 
                                                elements.find { 
                                                  LookupElementPresentation p = new LookupElementPresentation();
                                                  it.renderElement(p)
                                                  it.lookupString == "getProperty" && p.tailText == "(String key)"})
    myFixture.checkResult("class C { void m() { System.getProperty(<caret>) } }")

    def actual = JavaExternalDocumentationTest.getDocumentationText(editor)
    assert actual.contains("<code>null</code> if there is no property with that key")
  }

  private void configure(String text) {
    myFixture.configureByText 'a.java', text
  }

  void doTestCtrlHoverDoc(String inputFile, String expectedDoc) {
    configure inputFile.stripIndent()
    String doc = CtrlMouseHandler.getInfo(myFixture.editor, CtrlMouseHandler.BrowseMode.Declaration)
    assert doc == expectedDoc
  }
}

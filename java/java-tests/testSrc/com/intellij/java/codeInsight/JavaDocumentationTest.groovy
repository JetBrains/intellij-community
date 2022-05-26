// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.javadoc.DocumentationDelegateProvider
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import groovy.transform.CompileStatic
/**
 * @author peter
 */
@CompileStatic
class JavaDocumentationTest extends LightJavaCodeInsightFixtureTestCase {
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
      """<div class='definition'><pre><span style="color:#000080;font-weight:bold;">void</span>&nbsp;<span style="color:#000000;">doFoo</span><span style="">(</span><span style="">)</span></pre></div><table class='sections'><p></table><div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a></div>"""

    assert doc == expected
  }

  void testGenericMethod() {
    doTestCtrlHoverDoc("""\
      class Bar<T> { java.util.List<T> foo(T param); }

      class Foo {{
        new Bar<String>().f<caret>oo();
      }}""",
    """<span style="color:#000000;"><a href="psi_element://Bar">Bar</a></span><br/> <span style="color:#000000;"><a href="psi_element://java.util.List">List</a></span><span style="">&lt;</span><span style="color:#000000;">String</span><span style="">&gt;</span> <span style="color:#000000;">foo</span><span style="">(</span><span style="color:#000000;">String</span> <span style="">param</span><span style="">)</span>""")
  }

  void testGenericField() {
    doTestCtrlHoverDoc("""\
      class Bar<T> { T field; }

      class Foo {{
        new Bar<Integer>().fi<caret>eld
      }}""",
      """<span style="color:#000000;"><a href="psi_element://Bar">Bar</a></span><br/> <span style="color:#000000;">Integer</span> <span style="color:#660e7a;font-weight:bold;">field</span>""")
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
      "<span style=\"color:#000080;font-weight:bold;\">private</span> <span style=\"color:#000080;font-weight:bold;\">void</span> <span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span>")
  }

  void testAnnotationInCtrlHoverDoc() {
    doTestCtrlHoverDoc("""\
      class Foo {
          {<caret>m();}
          @Anno
          private void m() {}
      }
      @java.lang.annotation.Documented
      @interface Anno {} """,
      """<span style="color:#000000;"><a href="psi_element://Foo">Foo</a></span><br/> <span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Anno">Anno</a></span>&nbsp;<br/><span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">void</span> <span style="color:#000000;">m</span><span style="">(</span><span style="">)</span>""")
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
      """<span style="color:#000000;"><a href="psi_element://C">C</a></span><br/> <span style="color:#000000;"><a href="psi_element://Outer.Inner">Outer.Inner</a></span> <span style="color:#660e7a;font-weight:bold;">field</span>""")
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
      "<div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span>&nbsp;<span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><div class='content'>\n  For example, <a href=\"psi_element://java.lang.String#String(byte[], int, int, java.lang.String)\"><code><span style=\"color:#0000ff;\">String</span><span style=\"\">.</span><span style=\"color:#0000ff;\">String</span><span style=\"\">(</span><span style=\"color:#000080;font-weight:bold;\">byte</span><span style=\"\">[],&#32;</span><span style=\"color:#000080;font-weight:bold;\">int</span><span style=\"\">,&#32;</span><span style=\"color:#000080;font-weight:bold;\">int</span><span style=\"\">,&#32;String)</span></code></a>.\n   </div><table class='sections'></table><div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://C\"><code><span style=\"color:#000000;\">C</span></code></a></div>"

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
      """<div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span>&nbsp;<span style="color:#000080;font-weight:bold;">void</span>&nbsp;<span style="color:#000000;">m</span><span style="">(</span><span style="">)</span></pre></div><div class='content'> Visit the "<code style='font-size:100%;'><span style=""><span style="">/login</span></span></code>" URL. </div><table class='sections'></table><div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://C"><code><span style="color:#000000;">C</span></code></a></div>"""

    assert doc == expected
  }

  void testInlineReturnJava16() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_16, { configure """\
      class C {
        /** {@return smth} */
        public String <caret>m() { }
      }""".stripIndent()
      def method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod.class)
      def doc = new JavaDocumentationProvider().generateRenderedDoc(method.getDocComment())

      def expected =
        '<div class=\'content\'> @return smth </div><table class=\'sections\'><tr><td valign=\'top\' class=\'section\'><p>Returns:</td><td valign=\'top\'><p> smth</td></table>'

      assert doc == expected
    })
  }

  void testMethodToMethodDelegate() {
    DocumentationDelegateProvider provider = {
      if (it instanceof PsiMethod && it.name == 'foo') {
        JavaPsiFacade.getInstance(project).findClass('Foo', it.resolveScope)?.findMethodBySignature(it, false)
      }
    }
    DocumentationDelegateProvider.EP_NAME.getPoint().registerExtension(provider, myFixture.testRootDisposable)

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

    String expected = "<div class=\'definition\'><pre><span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">foo</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><table class=\'sections\'><p><tr><td valign=\'top\' class=\'section\'><p>From class:</td><td valign=\'top\'><p><a href=\"psi_element://Foo\"><code><span style=\"color:#000000;\">Foo</span></code></a><br>\n  Some doc\n  </td></table><div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://Bar\"><code><span style=\"color:#000000;\">Bar</span></code></a></div>"

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
      "<html><div class='content-only'>Candidates for method call <b>s.regionMatches()</b> are:<br>" +
      "<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)\">boolean regionMatches(int, String, int, int)</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(boolean, int, java.lang.String, int, int)\">boolean regionMatches(boolean, int, String, int, int)</a><br>" +
      "</div>"

    assert actual == expected
  }

  void "test navigation updates decoration"() {
    def input = """\
      class Foo {
        void foo(String s) {
          s.region<caret>Matches()
        } 
      }""".stripIndent()

    def documentationManager = DocumentationManager.getInstance(myFixture.project)
    JavaExternalDocumentationTest.getDocumentationText(myFixture.project, input) { component ->
      def expected =
        "<html><div class='content-only'>Candidates for method call <b>s.regionMatches()</b> are:<br>" +
        "<br>" +
        "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)\">boolean regionMatches(int, String, int, int)</a><br>" +
        "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(boolean, int, java.lang.String, int, int)\">boolean regionMatches(boolean, int, String, int, int)</a><br>" +
        "</div>"

      assert component.decoratedText == expected

      documentationManager.navigateByLink(component, null, "psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)")
      try {
        JavaExternalDocumentationTest.waitTillDone(documentationManager.getLastAction())
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e)
      }

      // Here we check that the covering module (SDK in this case) is rendered in decorated info
      assert component.decoratedText.contains('<div class="bottom"><icon src="AllIcons.Nodes.PpLibFolder"/>&nbsp;&lt; java 1.7 &gt;</div>')
    }


  }

  private void configure(String text) {
    myFixture.configureByText 'a.java', text
  }

  void doTestCtrlHoverDoc(String inputFile, String expectedDoc) {
    configure inputFile.stripIndent()
    String doc = CtrlMouseHandler.getGoToDeclarationOrUsagesText(myFixture.editor)
    assert UIUtil.getHtmlBody(doc) == expectedDoc
  }
}

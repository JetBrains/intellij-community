/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
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
    "    </style>";

  public void testConstructorDoc() {
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

  public void testConstructorDoc2() {
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

  public void testMethodDocWhenInArgList() {
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

  public void testGenericMethod() {
    configure """\
      class Bar<T> { java.util.List<T> foo(T param); }

      class Foo {{
        new Bar<String>().f<caret>oo();
      }}""".stripIndent()

    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    def doc = CtrlMouseHandler.getInfo(ref.resolve(), ref.element)

    assert doc == "Bar\n java.util.List&lt;java.lang.String&gt; foo (java.lang.String param)"
  }

  public void testGenericField() {
    configure """\
      class Bar<T> { T field; }

      class Foo {{
        new Bar<Integer>().fi<caret>eld
      }}""".stripIndent()

    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    def doc = CtrlMouseHandler.getInfo(ref.resolve(), ref.element)

    assert doc == "Bar\n java.lang.Integer field"
  }
  
  public void testMethodInAnonymousClass() {
    configure """\
      class Foo {{
        new Runnable() {
          @Override
          public void run() {
            <caret>m();
          }

          private void m() {}
        }.run();
      }}""".stripIndent()

    def doc = CtrlMouseHandler.getInfo(editor, CtrlMouseHandler.BrowseMode.Declaration)

    assert doc == "private void m ()"
  }

  public void testAsterisksFiltering() {
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

  public void testInlineTagSpacing() {
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

  private void configure(String text) {
    myFixture.configureByText 'a.java', text
  }
}

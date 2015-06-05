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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class JavaDocumentationTest extends LightCodeInsightFixtureTestCase {

  public void testConstructorDoc() {
    configure '''
class Foo { Foo() {} Foo(int param) {} }

class Foo2 {{
  new Foo<caret>
}}
'''
    def originalElement = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    def doc = new JavaDocumentationProvider().generateDoc(
      DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file),
      originalElement
    )

    assert doc == """<html>Candidates for new <b>Foo</b>() are:<br>&nbsp;&nbsp;<a href="psi_element://Foo#Foo()">Foo()</a><br>&nbsp;&nbsp;<a href="psi_element://Foo#Foo(int)">Foo(int param)</a><br></html>"""
  }

  public void testConstructorDoc2() {
    configure '''
class Foo { Foo() {} Foo(int param) {} }

class Foo2 {{
  new Foo(<caret>)
}}
'''

    def elementAt = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    def exprList = PsiTreeUtil.getParentOfType(elementAt, PsiExpressionList.class)
    def doc = new JavaDocumentationProvider().generateDoc(
      exprList,
      elementAt
    )

    assert doc == """<html>Candidates for new <b>Foo</b>() are:<br>&nbsp;&nbsp;<a href="psi_element://Foo#Foo()">Foo()</a><br>&nbsp;&nbsp;<a href="psi_element://Foo#Foo(int)">Foo(int param)</a><br></html>"""
  }

  public void testMethodDocWhenInArgList() {
    configure '''
class Foo { void doFoo() {} }

class Foo2 {{
  new Foo().doFoo(<caret>)
}}
'''
    def exprList = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiExpressionList.class)
    def doc = new JavaDocumentationProvider().generateDoc(
      exprList,
      null
    )

    assert doc == """<html><head>    <style type="text/css">        #error {            background-color: #eeeeee;            margin-bottom: 10px;        }        p {            margin: 5px 0;        }    </style></head><body><small><b><a href="psi_element://Foo"><code>Foo</code></a></b></small><PRE>void&nbsp;<b>doFoo</b>()</PRE></body></html>"""
  }

  public void testGenericMethod() {
    configure '''
class Bar<T> { java.util.List<T> foo(T param); }

class Foo {{
  new Bar<String>().f<caret>oo();
}}
'''
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assert CtrlMouseHandler.getInfo(ref.resolve(), ref.element) == """Bar
 java.util.List&lt;java.lang.String&gt; foo (java.lang.String param)"""
  }

  public void testGenericField() {
    configure '''
class Bar<T> { T field; }

class Foo {{
  new Bar<Integer>().fi<caret>eld
}}
'''
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assert CtrlMouseHandler.getInfo(ref.resolve(), ref.element) == """Bar
 java.lang.Integer field"""
  }
  
  public void testMethodInAnonymousClass() {
    configure '''
class Foo {{
  new Runnable() {
    @Override
    public void run() {
      <caret>m();
    }
    
    private void m() {}
  }.run();
}}
'''
    assert CtrlMouseHandler.getInfo(editor, CtrlMouseHandler.BrowseMode.Declaration) == "private void m ()"
  }

  private void configure(String text) {
    myFixture.configureByText 'a.java', text
  }
}

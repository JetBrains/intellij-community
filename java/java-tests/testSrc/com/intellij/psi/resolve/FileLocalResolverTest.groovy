/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.resolve

import com.intellij.lang.FCTSBackedLighterAST
import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.lang.TreeBackedLighterAST
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.source.FileLocalResolver
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull

/**
 * @author peter
 */
public class FileLocalResolverTest extends LightCodeInsightFixtureTestCase {

  public void "test unknown variable"() {
    assertDoesNotResolve 'class C {{ <caret>a = 2; }}'
  }

  public void "test code block variable"() {
    assertResolves '''class C {{
  int a;
  int b;
  {
    <caret>a = 2;
  }
}}'''
  }

  public void "test variables in the same declaration"() {
    assertResolves '''
class C {{
  int a = 2, b = <caret>a;
}}'''
  }

  public void "test inside for loop var"() {
    assertResolves '''
class C {{
  for (int a = 2, b = <caret>a;;) {}
}}'''
  }

  public void "test for loop var in body"() {
    assertResolves '''
class C {{
  for (int a = 2;;) { b = <caret>a; }
}}'''
  }

  public void "test for loop var in condition"() {
    assertResolves '''
class C {{
  for (int a = 2; <caret>a < 3;) {}
}}'''
  }

  public void "test for loop var in modification"() {
    assertResolves '''
class C {{
  for (int a = 2; ; <caret>a++) {}
}}'''
  }

  public void "test foreach loop var in body"() {
    assertResolves '''
class C {{
  for (Object x : xs) { <caret>x = null; }
}}'''
  }

  public void "test resource var in try body"() {
    assertResolves '''
class C {{
  try (Smth x = smth) { <caret>x = null; }
}}'''
  }

  public void "test resource var in second resource var"() {
    assertResolves '''
class C {{
  try (Smth x = smth; Smth y = <caret>x) { }
}}'''
  }

  public void "test no forward references in resource list"() {
    assertDoesNotResolve '''
class C {{
  try (Smth y = <caret>x; Smth x = smth) { }
}}'''
  }

  public void "test catch parameter"() {
    assertResolves '''
class C {{
  try { } catch (IOException e) { <caret>e = null; }
}}'''
  }

  public void "test single lambda parameter"() {
    assertResolves '''
class C {{
  Some r = param -> sout(para<caret>m);
}}'''
  }

  public void "test listed lambda parameter"() {
    assertResolves '''
class C {{
  Some r = (param, p2) -> sout(para<caret>m);
}}'''
  }

  public void "test typed lambda parameter"() {
    assertResolves '''
class C {{
  Some r = (String param, int p2) -> sout(<caret>p2);
}}'''
  }

  public void "test method parameter"() {
    assertResolves '''
class C { void foo(int param) {
  sout(para<caret>m);
}}'''
  }

  public void "test field"() {
    assertResolves '''
class C {
{
  sout(<caret>f);
}
int f = 2;
}'''
  }

  public void "test possible anonymous super class field"() {
    assert FileLocalResolver.LightResolveResult.UNKNOWN == configureAndResolve('''
class C {
{
  int a = 1;
  new SuperClass() {
  void foo() {
    print(<caret>a);
  }
  }
}
}''')
    assert FileLocalResolver.LightResolveResult.NON_LOCAL == configureAndResolve('''
class C {
{
  new SuperClass() {
  void foo() {
    print(<caret>a);
  }
  }
}
}''')
    assert FileLocalResolver.LightResolveResult.NON_LOCAL == configureAndResolve('''
class C {
int field = 1;
{
  new SuperClass() {
  void foo() {
    print(<caret>field);
  }
  }
}
}''')
  }

  public void "test no method resolving"() {
    assertDoesNotResolve '''
class C {
  int a = 1;
  { <caret>a(); }
}
'''
  }

  public void "test no qualified reference resolving"() {
    assertDoesNotResolve '''
class C {
  int a = 1;
  Object b;
  { b.<caret>a = null; }
}
'''
  }

  private assertDoesNotResolve(String fileText) {
    assert configureAndResolve(fileText) == FileLocalResolver.LightResolveResult.NON_LOCAL
  }

  private assertResolves(String fileText) {
    LighterASTNode result = configureAndResolve(fileText)?.target
    assert result
    assert result.startOffset == findReference().resolve().textRange.startOffset
  }

  private FileLocalResolver.LightResolveResult configureAndResolve(String fileText) {
    myFixture.configureByText 'a.java', fileText

    def astTree = new TreeBackedLighterAST(myFixture.file.node)
    def astRef = TreeBackedLighterAST.wrap(findReference().element.node)
    def astResult = new FileLocalResolver(astTree).resolveLocally(astRef)

    def fctsTree = PsiFileFactory.getInstance(project).createFileFromText('a.java', JavaLanguage.INSTANCE, myFixture.file.text, false, false).node.lighterAST
    assert fctsTree instanceof FCTSBackedLighterAST
    def fctsRef = findEquivalentNode(fctsTree, astRef)
    def fctsResult = new FileLocalResolver(fctsTree).resolveLocally(fctsRef)
    assert fctsResult == astResult || nodesEqual(fctsResult.target, astResult.target)

    return astResult
  }

  private LighterASTNode findEquivalentNode(LighterAST fctsTree, LighterASTNode target) {
    LighterASTNode fctsRef = null
    new RecursiveLighterASTNodeWalkingVisitor(fctsTree) {
      @Override
      void visitNode(@NotNull LighterASTNode element) {
        if (nodesEqual(element, target)) fctsRef = element
        super.visitNode(element)
      }
    }.visitNode(fctsTree.root)
    assert fctsRef
    return fctsRef
  }

  private static boolean nodesEqual(LighterASTNode node1, LighterASTNode node2) {
    return node1.startOffset == node2.startOffset && node1.endOffset == node2.endOffset && node1.tokenType == node2.tokenType
  }

  private PsiReference findReference() {
    return myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
  }

  public void "test short field type name"() {
    assert 'Bar' == getShortClassTypeName('''
class Foo {
  foo.Bar <caret>f;
}''')
  }

  public void "test short field type name that coincides with a type parameter"() {
    assert 'Bar' == getShortClassTypeName('''
class Foo<Bar> {
  foo.Bar <caret>f;
}''')
  }

  public void "test generic field type name"() {
    assert null == getShortClassTypeName('''
class Foo<Bar> {
  Bar <caret>f;
}''')
  }

  public void "test variable type name in a static method"() {
    assert 'Bar' == getShortClassTypeName('''
class Foo<Bar> {
  static void foo() { Bar <caret>f; }
}''')
  }

  public void "test variable type name in an instance method"() {
    assert null == getShortClassTypeName('''
class Foo<Bar> {
  void foo() { Bar <caret>f; }
}''')
  }

  public void "test parameter type name in a generic method"() {
    assert null == getShortClassTypeName('''
class Foo<Bar> {
  <Bar> void foo(Bar <caret>b) { }
}''')
  }

  public void "test missing lambda parameter type name"() {
    assert null == getShortClassTypeName('''
class Foo {{ I i = <caret>a -> a }}''')
  }

  public void "test primitive var type name"() {
    assert null == getShortClassTypeName('''
class Foo {{ int <caret>i = 2; }}''')
  }

  public void "test array var type name"() {
    assert null == getShortClassTypeName('''
class Foo {{ String[] <caret>i = 2; }}''')
  }

  private String getShortClassTypeName(String text) {
    myFixture.configureByText 'a.java', text
    def var = PsiTreeUtil.findElementOfClassAtOffset(myFixture.file, myFixture.editor.caretModel.offset, PsiVariable.class, false)
    def tree = new TreeBackedLighterAST(myFixture.file.node)
    def varNode = TreeBackedLighterAST.wrap(var.node)
    return new FileLocalResolver(tree).getShortClassTypeName(varNode)
  }
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaCodeFragmentFactory
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.PairFunction

/**
 * @author peter
 */
public class FragmentCompletionTest extends LightCodeInsightFixtureTestCase {
  public void testDontCompleteFieldsAndMethodsInReferenceCodeFragment() throws Throwable {
    final String text = CommonClassNames.JAVA_LANG_OBJECT + ".<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, null, true, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assert !myFixture.completeBasic()
    myFixture.checkResult(text);
  }

  public void testNoKeywordsInReferenceCodeFragment() throws Throwable {
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment("<caret>", null, true, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assert myFixture.completeBasic()
    assert !('package' in myFixture.lookupElementStrings)
    assert !('import' in myFixture.lookupElementStrings)
  }

  public void "test no classes in reference code fragment"() throws Throwable {
    myFixture.addClass("package foo; public interface FooIntf { }")

    def text = "FooInt<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, null, true, false);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assert !myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult(text)
  }

  public void "test no constants in reference code fragment"() throws Throwable {
    myFixture.addClass("package foo; public interface FooIntf { int constant = 2 }")

    def text = "FooInt.con<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, null, true, false);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assert !myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult(text)
  }

  public void testNoPackagesInExpressionCodeFragment() throws Throwable {
    final String text = "jav<caret>";
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, null, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assert !myFixture.completeBasic()
    myFixture.checkResult(text);
  }

  public void testSubPackagesInExpressionCodeFragment() throws Throwable {
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("java.la<caret>", null, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assert !myFixture.completeBasic()
    myFixture.checkResult("java.lang.<caret>");
  }

  public void testPrimitivesInTypeCodeFragmentWithParameterListContext() throws Throwable {
    def clazz = myFixture.addClass("class Foo { void foo(int a) {} }")

    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment("b<caret>", clazz.methods[0].parameterList, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0..1] == ['boolean', 'byte']
  }

  public void testQualifierCastingInExpressionCodeFragment() throws Throwable {
    final ctxText = "class Bar {{ Object o; o=null }}"
    final ctxFile = createLightFile(StdFileTypes.JAVA, ctxText)
    final context = ctxFile.findElementAt(ctxText.indexOf("o="))
    assert context

    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("o instanceof String && o.subst<caret>", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assert !myFixture.completeBasic()
    myFixture.checkResult("o instanceof String && ((String) o).substring(<caret>)");
  }

  public void testNoGenericQualifierCastingWithRuntimeType() throws Throwable {
    final ctxText = "import java.util.*; class Bar {{ Map<Integer,Integer> map = new HashMap<Integer,Integer>(); map=null; }}"
    final ctxFile = createLightFile(StdFileTypes.JAVA, ctxText)
    final context = ctxFile.findElementAt(ctxText.indexOf("map="))
    assert context
    
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("map.entry<caret>", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.file.putCopyableUserData(JavaCompletionUtil.DYNAMIC_TYPE_EVALUATOR, new PairFunction<PsiExpression, CompletionParameters, PsiType>() {
      @Override
      PsiType fun(PsiExpression t, CompletionParameters v) {
        return JavaPsiFacade.getElementFactory(t.project).createTypeByFQClassName(CommonClassNames.JAVA_UTIL_HASH_MAP)
      }
    })
    assert !myFixture.completeBasic()
    myFixture.checkResult("map.entrySet()<caret>");
  }

  public void "test no static after instance in expression fragment"() {
    def ctxFile = myFixture.addClass("package foo; public class Class {{\n int a = 2; }}").containingFile
    def context = ctxFile.findElementAt(ctxFile.text.indexOf('int'))

    def text = "Double.valueOf(2).v<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic()
    assert !myFixture.lookupElementStrings.contains('valueOf')
  }

  public void "test no class keywords in expression fragment"() {
    def ctxFile = myFixture.addClass("package foo; public class Class {{\n int a = 2; }}").containingFile
    def context = ctxFile.findElementAt(ctxFile.text.indexOf('int'))

    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic()
    assert !myFixture.lookupElementStrings.contains('enum')
    assert !myFixture.lookupElementStrings.contains('class')
  }

  public void "test annotation context"() {
    def ctxFile = myFixture.addClass("class Class { void foo(int context) { @Anno int a; } }").containingFile
    def context = ctxFile.findElementAt(ctxFile.text.indexOf('Anno'))
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("c<caret>", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings.contains('context')
  }

}

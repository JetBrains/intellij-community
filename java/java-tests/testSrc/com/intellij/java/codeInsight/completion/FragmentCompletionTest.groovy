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
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.PairFunction
import groovy.transform.CompileStatic

@CompileStatic
class FragmentCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  void testDontCompleteFieldsAndMethodsInReferenceCodeFragment() throws Throwable {
    final String text = CommonClassNames.JAVA_LANG_OBJECT + ".<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, null, true, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    assert !myFixture.completeBasic()
    myFixture.checkResult(text)
  }

  void testNoKeywordsInReferenceCodeFragment() throws Throwable {
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment("<caret>", null, true, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    assert myFixture.completeBasic()
    assert !('package' in myFixture.lookupElementStrings)
    assert !('import' in myFixture.lookupElementStrings)
  }

  void "test no classes in reference code fragment"() throws Throwable {
    myFixture.addClass("package foo; public interface FooIntf { }")

    def text = "FooInt<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, null, true, false)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    assert !myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult(text)
  }

  @NeedsIndex.Full
  void testPreferClassOverPackage() {
    myFixture.addClass("package Xyz; public class Xyz {}")

    def text = "Xy<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, null, true, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    def elements = myFixture.completeBasic()
    assert elements.length == 2
    assert elements[0].getPsiElement() instanceof PsiClass
    assert elements[1].getPsiElement() instanceof PsiPackage
  }

  void "test no constants in reference code fragment"() throws Throwable {
    myFixture.addClass("package foo; public interface FooIntf { int constant = 2 }")

    def text = "FooInt.con<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createReferenceCodeFragment(text, null, true, false)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    assert !myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult(text)
  }

  void testNoPackagesInExpressionCodeFragment() throws Throwable {
    final String text = "jav<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, null, null, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    assert !myFixture.completeBasic()
    myFixture.checkResult(text)
  }

  void testSubPackagesInExpressionCodeFragment() throws Throwable {
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("java.la<caret>", null, null, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    assert !myFixture.completeBasic()
    myFixture.checkResult("java.lang.<caret>")
  }

  void testPrimitivesInTypeCodeFragmentWithParameterListContext() throws Throwable {
    def clazz = myFixture.addClass("class Foo { void foo(int a) {} }")

    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment("b<caret>", clazz.methods[0].parameterList, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings[0..1] == ['boolean', 'byte']
  }

  @NeedsIndex.ForStandardLibrary
  void testQualifierCastingInExpressionCodeFragment() throws Throwable {
    final ctxText = "class Bar {{ Object o; o=null }}"
    final ctxFile = createLightFile(JavaFileType.INSTANCE, ctxText)
    final context = ctxFile.findElementAt(ctxText.indexOf("o="))
    assert context

    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("o instanceof String && o.subst<caret>", context, null, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    assert !myFixture.completeBasic()
    myFixture.checkResult("o instanceof String && ((String) o).substring(<caret>)")
  }

  @NeedsIndex.ForStandardLibrary
  void testNoGenericQualifierCastingWithRuntimeType() throws Throwable {
    final ctxText = "import java.util.*; class Bar {{ Map<Integer,Integer> map = new HashMap<Integer,Integer>(); map=null; }}"
    final ctxFile = createLightFile(JavaFileType.INSTANCE, ctxText)
    final context = ctxFile.findElementAt(ctxText.indexOf("map="))
    assert context

    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("map.entry<caret>", context, null, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    myFixture.file.putCopyableUserData(JavaCompletionUtil.DYNAMIC_TYPE_EVALUATOR, new PairFunction<PsiExpression, CompletionParameters, PsiType>() {
      @Override
      PsiType fun(PsiExpression t, CompletionParameters v) {
        return JavaPsiFacade.getElementFactory(t.project).createTypeByFQClassName(CommonClassNames.JAVA_UTIL_HASH_MAP)
      }
    })
    assert !myFixture.completeBasic()
    myFixture.checkResult("map.entrySet()<caret>")
  }

  void "test no static after instance in expression fragment"() {
    def ctxFile = myFixture.addClass("package foo; public class Class {{\n int a = 2; }}").containingFile
    def context = ctxFile.findElementAt(ctxFile.text.indexOf('int'))

    def text = "Double.valueOf(2).v<caret>"
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, context, null, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    myFixture.completeBasic()
    assert !myFixture.lookupElementStrings.contains('valueOf')
  }

  void "test no class keywords in expression fragment"() {
    def ctxFile = myFixture.addClass("package foo; public class Class {{\n int a = 2; }}").containingFile
    def context = ctxFile.findElementAt(ctxFile.text.indexOf('int'))

    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("", context, null, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    myFixture.completeBasic()
    assert !myFixture.lookupElementStrings.contains('enum')
    assert !myFixture.lookupElementStrings.contains('class')
  }

  @NeedsIndex.ForStandardLibrary
  void "test annotation context"() {
    def ctxFile = myFixture.addClass("class Class { void foo(int context) { @Anno int a; } }").containingFile
    def context = ctxFile.findElementAt(ctxFile.text.indexOf('Anno'))
    PsiFile file = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("c<caret>", context, null, true)
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings.contains('context')
  }

  @NeedsIndex.Full
  void "test proximity ordering in scratch-like file"() {
    def barField = myFixture.addClass('package bar; public class Field {}')
    def fooField = myFixture.addClass('package foo; public class Field {}')
    def text = 'import foo.Field; class C { Field<caret> }'
    def file = PsiFileFactory.getInstance(project).createFileFromText('a.java', JavaLanguage.INSTANCE, text, true, false)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    def items = myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'Field', 'Field'
    assert fooField == items[0].object
    assert barField == items[1].object
  }

  @NeedsIndex.ForStandardLibrary
  void "test package default class in code fragment"() {
    myFixture.addClass "class ABCD {}"
    PsiJavaCodeReferenceCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment("ABC<caret>", null, true, true)
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE)
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile())
    myFixture.complete(CompletionType.BASIC)
    assert myFixture.lookupElements.find { (it.lookupString == "ABCD") } != null
  }

  @NeedsIndex.Full
  void "test qualified class in code fragment"() {
    myFixture.addClass "package foo; public class Foo1 {}"
    PsiJavaCodeReferenceCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment("Foo<caret>", null, true, true)
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE)
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile())
    myFixture.completeBasic()
    assert myFixture.lookupElements.find { (it.lookupString == "Foo1") } != null
    myFixture.type('\n')
    myFixture.checkResult("foo.Foo1");
  }

}

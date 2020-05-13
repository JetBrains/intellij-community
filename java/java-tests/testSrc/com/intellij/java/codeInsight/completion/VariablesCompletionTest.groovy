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

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import groovy.transform.CompileStatic

@CompileStatic
class VariablesCompletionTest extends LightFixtureCompletionTestCase {
  public static final String FILE_PREFIX = "/codeInsight/completion/variables/"

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath()
  }

  void testObjectVariable() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java")
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java")
  }

  void testStringVariable() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java")
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java")
  }

  void testInputMethodEventVariable() throws Exception {
    myFixture.addClass("package java.awt.event; public interface InputMethodEvent {}")

    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java")
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java")
  }

  void testLocals1() throws Exception {
    doSelectTest("TestSource1.java", "TestResult1.java")
  }

  void testInterfaceMethod() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "InterfaceMethod.java")
    assertStringItems("calcGooBarDoo", "calcBarDoo", "calcDoo")
  }

  void testLocals2() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "TestSource2.java")
    myFixture.assertPreferredCompletionItems 0, 'abc', 'aaa'
    checkResultByFile(FILE_PREFIX + "locals/" + "TestResult2.java")
  }

  void testLocals3() throws Exception {
    doTest("TestSource3.java", "TestResult3.java")
  }

  void testLocals4() throws Exception {
    doSelectTest("TestSource4.java", "TestResult4.java")
  }

  void testLocals5() throws Exception {
    doTest("TestSource5.java", "TestResult5.java")
  }

  void testLocals6() throws Exception {
    doSelectTest("TestSource6.java", "TestResult6.java")
  }

  void testLocals7() throws Exception {
    doTest("TestSource7.java", "TestResult7.java")
  }

  void testLocalReserved() throws Exception {
    doTest("LocalReserved.java", "LocalReserved_after.java")
  }

  void testLocalReserved2() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "LocalReserved2.java")
    checkResultByFile(FILE_PREFIX + "locals/" + "LocalReserved2.java")
    assert !myFixture.lookupElementStrings
  }

  void testUniqueNameInFor() throws Exception {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java")
  }

  void testWithBuilderParameter() throws Exception {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java")
  }

  private void doTest(String before, String after) throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + before)
    checkResultByFile(FILE_PREFIX + "locals/" + after)
  }

  private void doSelectTest(String before, String after) throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + before)
    myFixture.type('\n')
    checkResultByFile(FILE_PREFIX + "locals/" + after)
  }

  void testLocals8() throws Exception {
    doTest("TestSource8.java", "TestResult8.java")
  }

  void testUnresolvedReference() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java")
    assertStringItems("o", "psiClass")
  }

  void testFieldNameCompletion1() throws Exception {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject())
    settings.FIELD_NAME_PREFIX = "my"
      doSelectTest("FieldNameCompletion1.java", "FieldNameCompletion1-result.java")
  }

  void testFieldNameCompletion2() throws Exception {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject())
    settings.FIELD_NAME_PREFIX = "my"
    configureByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion2.java")
    checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion2-result.java")
  }

  void testFieldNameCompletion3() throws Exception {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject())
    settings.FIELD_NAME_PREFIX = "my"
    configureByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion3.java")
    complete()
    checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion3-result.java")
  }

  void testLocals9() throws Exception {
    doSelectTest("TestSource9.java", "TestResult9.java")
  }

  void testFieldOutOfAnonymous() throws Exception {
    doTest("TestFieldOutOfAnonymous.java", "TestFieldOutOfAnonymousResult.java")
  }

  void testUnresolvedMethodName() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName.java")
    complete()
    checkResultByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName_after.java")
  }

  void testArrayMethodName() throws Throwable {
    doTest("ArrayMethodName.java", "ArrayMethodName-result.java")
  }

  void "test suggest fields that could be initialized by super constructor"() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
class Foo extends java.util.ArrayList {
  int field;
  int field2;
  Foo() {
    super();
    field2 = f<caret>; 
  } 
}""")
    complete()
    myFixture.assertPreferredCompletionItems 0, 'field', 'float'
  }

  void "test suggest fields initialized by init"() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
class Foo {
  final Integer field;
  
  { field = 42; }
  
  Foo() {
    equals(f<caret>); 
  } 
}""")
    complete()
    myFixture.assertPreferredCompletionItems 0, 'field'
  }

  void "test suggest fields in their modification inside constructor"() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
class Foo {
  int total;

  Foo() {
    this.total = 0;
    this.total += this.to<caret>tal
  }
}
""")
    complete()
    myFixture.assertPreferredCompletionItems 0, 'total'
  }

  void testInitializerMatters() throws Exception {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Foo {{ String f<caret>x = getFoo(); }; String getFoo() {}; }")
    complete()
    assertStringItems("foo")
  }

  void testFieldInitializerMatters() throws Exception {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Foo { String f<caret>x = getFoo(); String getFoo() {}; }")
    complete()
    assertStringItems("foo", "fString")
  }

  void testNoKeywordsInForLoopVariableName() throws Throwable {
    configure()
    assertStringItems("stringBuffer", "buffer")
  }

  void testDontIterateOverLoopVariable() throws Throwable {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'nodes', 'new', 'null'
  }

  void testDuplicateSuggestionsFromUsage() {
    configure()
    assertStringItems("preferencePolicy", "policy", "aPreferencePolicy")
  }

  void testSuggestVariablesInTypePosition() {
    configure()
    assertStringItems("myField", "myField2")
  }

  void configure() {
    configureByFile(FILE_PREFIX + getTestName(false) + ".java")
  }

  void testAnnotationValue() {
    configure()
    checkResultByFile(FILE_PREFIX + getTestName(false) + "_after.java")
  }

  void testConstructorParameterName() {
    configure()
    assertStringItems("color")
  }

  void testConstructorParameterNameWithPrefix() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject())
    settings.FIELD_NAME_PREFIX = "my"
    settings.PARAMETER_NAME_PREFIX = "p"

    configure()

    assertStringItems("pColor")
  }

  void "test finish with ="() {
    myFixture.configureByText 'a.java', '''
class FooFoo {
  FooFoo f<caret>
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'fooFoo', 'foo'
    myFixture.type '='
    myFixture.checkResult '''
class FooFoo {
  FooFoo fooFoo = <caret>
}
'''
  }

  void "test suggest variable names by non-getter initializer call"() {
    myFixture.configureByText 'a.java', '''
class FooFoo {
  { long <caret>x = System.nanoTime(); }
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'l', 'nanoTime', 'time'
  }

  void "test use superclass for inner class variable name suggestion"() {
    myFixture.configureByText 'a.java', '''
class FooFoo {
  { Rectangle2D.Double <caret>x }
}
class Rectangle2D {
  static class Double extends Rectangle2D {}
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'aDouble', 'rectangle2D'
  }

  void "test suggest field-shadowing parameter name"() {
    myFixture.configureByText 'a.java', '''
class FooFoo {
  private final Collection<MaterialQuality> materialQualities;

    public Inventory setMaterialQualities(Iterable<MaterialQuality> <caret>) {

    }}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'materialQualities', 'materialQualities1', 'qualities', 'materialQualityIterable', 'qualityIterable', 'iterable'
  }

  void "test suggest parameter name from javadoc"() {
    myFixture.configureByText 'a.java', '''
class FooFoo {
    /**
    * @param existing
    * @param abc
    * @param def
    * @param <T>
    */
    void set(int existing, int <caret>) {}
}
'''
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings as Set == ['abc', 'def', 'i'] as Set
  }

  void "test no name suggestions when the type is unresolved because it is actually a mistyped keyword"() {
    myFixture.configureByText 'a.java', '''
class C {
    { 
      retur Fi<caret>x
    }
    }
'''
    assert myFixture.completeBasic()?.size() == 0
  }

  void "test suggest variable name when the type is unresolved but does not seem a mistyped keyword"() {
    myFixture.configureByText 'a.java', '''
class C {
    { 
      UndefinedType <caret>
    }
    }
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'undefinedType', 'type'
  }

  void "test class-based suggestions for exception type"() {
    myFixture.configureByText 'a.java', '''
class C {
    { 
      try { } catch (java.io.IOException <caret>)
    }
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'e', 'ioException', 'exception'
  }

}

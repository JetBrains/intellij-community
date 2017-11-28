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
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.util.ObjectUtils
import com.intellij.util.ProcessingContext
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.NotNull

/**
 * @author mike
 */
class JavadocCompletionTest extends LightFixtureCompletionTestCase {
  private CodeStyleSettings settings
  private JavaCodeStyleSettings javaSettings

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/javadoc/"
  }

  @Override
  protected void setUp() {
    super.setUp()
    settings = CodeStyleSettingsManager.getSettings(getProject())
    javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class)
    myFixture.enableInspections(new JavaDocLocalInspection())
  }

  @Override
  protected void tearDown() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    super.tearDown()
  }

  void testNamesInPackage() {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("package-info.java", "p/package-info.java"))
    complete()
    assertStringItems("author", 'author ' + SystemProperties.getUserName(), "deprecated", "see", "since", "version")
  }

  void testNamesInClass() {
    configureByFile("ClassTagName.java")
    assertStringItems("author", 'author ' + SystemProperties.getUserName(), "deprecated", "param", "see", "serial", "since", "version")
  }

  void testNamesInField() {
    configureByFile("FieldTagName.java")
    assertStringItems("deprecated", "see", "serial", "serialField", "since")
  }

  void testNamesInMethod0() {
    configureByFile("MethodTagName0.java")
    assertStringItems("deprecated", "exception", "param", "return", "see", "serialData", "since", "throws")
  }

  void testNamesInMethod1() {
    configureByFile("MethodTagName1.java")
    assertStringItems("see", "serialData", "since", "throws")
  }

  void testParamValueCompletion() {
    configureByFile("ParamValue0.java")
    assertStringItems("a", "b", "c")
  }

  void testParamValueWithPrefixCompletion() {
    configureByFile("ParamValue1.java")
    assertStringItems("a1", "a2", "a3")
  }

  void testDescribedParameters() {
    configureByFile("ParamValue2.java")
    assertStringItems("a2", "a3")
  }

  void testSee0() {
    configureByFile("See0.java")
    myFixture.assertPreferredCompletionItems(0, "foo", "clone", "equals", "hashCode")
  }

  void testSee1() {
    configureByFile("See1.java")
    assertStringItems("notify", "notifyAll")
  }

  void testSee2() {
    configureByFile("See2.java")
    assertStringItems("notify", "notifyAll")
  }

  void testSee3() {
    configureByFile("See3.java")

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myField")))
  }

  @NotNull
  private List<String> getLookupElementStrings() {
    return ObjectUtils.assertNotNull(myFixture.getLookupElementStrings())
  }

  void testSee4() {
    configureByFile("See4.java")

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("A", "B", "C")))
  }

  void testSee5() {
    configureByFile("See5.java")

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myName")))
  }

  void testIDEADEV10620() {
    configureByFile("IDEADEV10620.java")

    checkResultByFile("IDEADEV10620-after.java")
  }

  void testException0() {
    configureByFile("Exception0.java")
    assertStringItems("deprecated", "exception", "param", "see", "serialData", "since", "throws")
  }

  void testException1() {
    configureByFile("Exception1.java")
    assertTrue(myItems.length > 18)
  }

  void testException2() {
    myFixture.configureByFile("Exception2.java")
    myFixture.complete(CompletionType.SMART)
    assertStringItems("IllegalStateException", "IOException")
  }

  void testInlineLookup() {
    configureByFile("InlineTagName.java")
    assertStringItems("code", "docRoot", "inheritDoc", "link", "linkplain", "literal", "value")
  }

  void testFinishWithSharp() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    try {
      checkFinishWithSharp()
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old
    }
  }

  private void checkFinishWithSharp() {
    myFixture.configureByFile("FinishWithSharp.java")
    myFixture.completeBasic()
    type('#')
    checkResultByFile("FinishWithSharp_after.java")
    final List<LookupElement> items = getLookup().getItems()
    assertEquals("bar", items.get(0).getLookupString())
    assertEquals("foo", items.get(1).getLookupString())
  }

  void testShortenClassName() throws Throwable {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    doTest()
  }

  void testMethodBeforeSharp() throws Throwable {
    doTest()
  }

  void testFieldReferenceInInnerClassJavadoc() throws Throwable {
    doTest()
  }

  void testShortenClassReference() throws Throwable {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    doTest()
  }

  void testQualifiedClassReference() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testQualifiedImportedClassReference() throws Throwable { doTest() }

  void testThrowsNonImported() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    checkResultByFile(getTestName(false) + "_after.java")
  }

  private void doTest() {
    configureByFile(getTestName(false) + ".java")
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testInlinePackageReferenceCompletion() {
    configureByFile("InlineReference.java")
    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("io", "lang", "util")))
  }

  void testQualifyClassReferenceInPackageStatement() {
    configureByFile(getTestName(false) + ".java")
    myFixture.type('\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void "test suggest param names"() {
    myFixture.configureByText "a.java", '''
class Foo {
  /**
  * @par<caret>
  */
  void foo(int intParam, Object param2) {
  }
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'param', 'param intParam', 'param param2'
    myFixture.type('\n intParam\n@para')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'param', 'param param2'
  }

  void "test fqns in package info"() {
    myFixture.configureByText "package-info.java", '''
/**
 * {@link java.util.Map#putA<caret>}
 */
'''
    myFixture.complete(CompletionType.BASIC)
    myFixture.checkResult '''
/**
 * {@link java.util.Map#putAll(java.util.Map)}
 */
'''
  }

  void "test suggest same param descriptions"() {
    myFixture.configureByText "a.java", '''
class Foo {
  /**
  * @param intParam so<caret> xxx
  * @throws Foo
  */
  void foo2(int intParam, Object param2) { }

  /**
  * @param intParam some integer param
  */
  void foo(int intParam, Object param2) { }
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'some integer param', 'some'
    myFixture.type('\t')
    myFixture.checkResult '''
class Foo {
  /**
  * @param intParam some integer param<caret>
  * @throws Foo
  */
  void foo2(int intParam, Object param2) { }

  /**
  * @param intParam some integer param
  */
  void foo(int intParam, Object param2) { }
}
'''
  }

  void "test suggest same param descriptions with no text after param name"() {
    myFixture.configureByText "a.java", '''
class Foo {
  /**
  * @param intParam <caret>
  * @throws Foo
  */
  void foo2(int intParam, Object param2) { }

  /**
  * @param intParam some integer param
  */
  void foo(int intParam, Object param2) { }
}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'some integer param'
  }

  void "test see super class"() {
    myFixture.addClass("package foo; public interface Foo {}")
    myFixture.addClass("package bar; public class Bar {} ")
    myFixture.configureByText "a.java", '''
import foo.*;
import bar.*;

/**
 * @se<caret>
 */
class Impl extends Bar implements Foo {}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'see', 'see bar.Bar', 'see foo.Foo'
  }

  void testShortenMethodParameterTypes() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    myFixture.addClass("package foo; public class Foo {}")
    myFixture.addClass("package bar; public class Bar {}")
    myFixture.configureByText "a.java", '''
import foo.*;
import bar.*;

/**
* {@link #go<caret>
*/
class Goo { void goo(Foo foo, Bar bar) {} }
'''
    myFixture.completeBasic()
    assert myFixture.editor.document.text.contains('@link #goo(Foo, Bar)')
  }

  void testNoMethodsAfterClassDot() {
    def text = '''
/**
* @see java.util.List.<caret>
*/
class Goo { void goo(Foo foo, Bar bar) {} }
'''
    myFixture.configureByText "a.java", text
    assert !myFixture.completeBasic()
    myFixture.checkResult(text)
  }

  void testShortNameInJavadocIfWasImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    def text = '''
import java.util.Map;

/**
 * {@link Ma<caret>}
 */
class Test {
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
import java.util.Map;

/**
 * {@link Map}
 */
class Test {
}
'''
  }

  void testFqnInJavadocIfWasNotImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    def text = '''
import java.util.Map;

/**
 * {@link HashMa<caret>}
 */
class Test {
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
import java.util.Map;

/**
 * {@link java.util.HashMap}
 */
class Test {
}
'''
  }


  void testFqnNameInJavadocIfWasImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS
    def text = '''
import java.util.Map;

/**
 * {@link Ma<caret>}
 */
class Test {
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
import java.util.Map;

/**
 * {@link java.util.Map}
 */
class Test {
}
'''
  }

  void testShortNameInJavadoc() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    def text = '''
import java.util.Map;

/**
 * {@link Ma<caret>}
 */
class Test {
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
import java.util.Map;

/**
 * {@link Map}
 */
class Test {
}
'''
  }

  void testShortNameInJavadocIfWasImportOnDemand() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    def text = '''
import java.util.*;

/**
 * {@link ArraLi<caret>}
 */
class Test {
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
import java.util.*;

/**
 * {@link ArrayList}
 */
class Test {
}
'''
  }

  void testNullQualifiedName() {
    def text = '''
public class Test {

  public static void main(String[] args) {
    class Super {
    }

    /**
     * {@link Su<caret>}
     */
    Super aSuper = new Super();

  }
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
public class Test {

  public static void main(String[] args) {
    class Super {
    }

    /**
     * {@link Super}
     */
    Super aSuper = new Super();

  }
}
'''

  }

  void testShortNameIfImplicitlyImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    def text = '''
/**
 * {@link Str<caret>}
 */
class Test {
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
/**
 * {@link String}
 */
class Test {
}
'''
  }

  void testShortNameIfInnerClass() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    def text = '''
package pkg;

class Foo {

    /**
     * @throws FooE<caret>
     */
    void foo() {
    }

    static class FooException extends RuntimeException {}
}
'''
    myFixture.configureByText "a.java", text
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult '''
package pkg;

class Foo {

    /**
     * @throws FooException 
     */
    void foo() {
    }

    static class FooException extends RuntimeException {}
}
'''
  }

  void testCustomReferenceProvider() {
    PsiReferenceRegistrarImpl registrar =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE)
    PsiReferenceProvider provider = new PsiReferenceProvider() {
      @Override
      @NotNull
      PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
        def ref = new PsiReferenceBase<PsiElement>(element) {

          @Override
          PsiElement resolve() {
            return element
          }

          @Override
          @NotNull
          Object[] getVariants() {
            return ["1", "2", "3"]
          }
        }
        return [ref]
      }
    }
    try {
      registrar.registerReferenceProvider(PsiDocTag.class, provider)
      configureByFile("ReferenceProvider.java")
      assertStringItems("1", "2", "3")
    }
    finally {
      registrar.unregisterReferenceProvider(PsiDocTag.class, provider)
    }
  }

  void "test complete author name"() {
    def userName = SystemProperties.userName
    assert userName
    myFixture.configureByText 'a.java', "/** @author <caret> */"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult "/** @author $userName<caret> */"
  }

  void "test insert link to class"() {
    myFixture.configureByText 'a.java', "/** FileNotFoEx<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@link java.io.FileNotFoundException<caret>} */"
  }

  void "test insert link to inner class"() {
    myFixture.addClass('package zoo; public class Outer { public static class FooBarGoo{}}')
    myFixture.configureByText 'a.java', "/** FooBarGo<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@link zoo.Outer.FooBarGoo<caret>} */"
  }

  void "test insert link to imported class"() {
    myFixture.configureByText 'a.java', "import java.io.*; /** FileNotFoEx<caret> */ class A{}"
    myFixture.completeBasic()
    myFixture.checkResult "import java.io.*; /** {@link FileNotFoundException<caret>} */ class A{}"
  }

  void "test insert link to method"() {
    myFixture.configureByText 'a.java', "/** a. #fo<caret> */ interface Foo { void foo(int a); }}"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult "/** a. {@link #foo(int)}<caret> */ interface Foo { void foo(int a); }}"
  }

  void "test insert link to field"() {
    myFixture.configureByText 'a.java', "/** a. #fo<caret> */ interface Foo { int foo; }}"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult "/** a. {@link #foo}<caret> */ interface Foo { int foo; }}"
  }

  void "test wrap null into code tag"() {
    myFixture.configureByText 'a.java', "/** nul<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@code null}<caret> */"
  }

  void "test null inside code tag"() {
    myFixture.configureByText 'a.java', "/** {@code nul<caret>} */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@code null<caret>} */"
  }

  void "test completing inside qualified name"() {
    myFixture.configureByText 'a.java', "/** @see java.io.<caret> */"
    myFixture.completeBasic()
    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == 'IOException' }
    myFixture.type('\n')
    myFixture.checkResult "/** @see java.io.IOException<caret> */"
  }

  void "test no hierarchical generic method duplicates"() {
    myFixture.configureByText 'a.java', """
interface Foo<T> {
    void foo(T t);
}

/**
 * {@link Bar#f<caret>} 
 */
interface Bar<T> extends Foo<T> {
    void foo(T t);
}"""
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'foo', 'finalize'
  }
}
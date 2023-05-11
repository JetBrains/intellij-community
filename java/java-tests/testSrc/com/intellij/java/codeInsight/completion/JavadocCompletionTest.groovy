// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection
import com.intellij.lang.java.JavaLanguage
import com.intellij.patterns.PlatformPatterns
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import com.intellij.util.ProcessingContext
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.NotNull

class JavadocCompletionTest extends LightFixtureCompletionTestCase {
  private JavaCodeStyleSettings javaSettings

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/javadoc/"
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST
  }

  @Override
  protected void setUp() {
    super.setUp()
    javaSettings = JavaCodeStyleSettings.getInstance(getProject())
    myFixture.enableInspections(new JavadocDeclarationInspection())
  }

  void testNamesInPackage() {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("package-info.java", "p/package-info.java"))
    complete()
    assertStringItems("author", 'author ' + SystemProperties.getUserName(), "deprecated", "see", "since", "version")
  }

  void testNamesInClass() {
    configureByFile("ClassTagName.java")
    assertStringItems("apiNote", "author", "author " + SystemProperties.getUserName(), "deprecated", "hidden", "implNote", "implSpec", "see", "serial", "since", "version")
  }

  void testNamesInField() {
    configureByFile("FieldTagName.java")
    assertStringItems("apiNote", "deprecated", "hidden", "implNote", "implSpec", "see", "serial", "serialField", "since")
  }

  void testNamesInMethod0() {
    configureByFile("MethodTagName0.java")
    assertStringItems("apiNote", "deprecated", "exception", "hidden", "implNote", "implSpec", "return", "see", "serialData", "since", "throws")
  }

  void testNamesInMethod1() {
    configureByFile("MethodTagName1.java")
    assertStringItems("see", "serialData", "since", "implSpec", "throws")
  }

  void testParamValueCompletion() {
    configureByFile("ParamValue0.java")
    assertStringItems("a", "b", "c", "<A>", "<B>")
  }

  void testParamValueWithPrefixCompletion() {
    configureByFile("ParamValue1.java")
    assertStringItems("a1", "a2", "a3")
  }

  void testTypeParamValueWithPrefix() {
    configureByTestName()
    assertStringItems("<A>", "<B>")
  }

  void testDescribedParameters() {
    configureByFile("ParamValue2.java")
    assertStringItems("a2", "a3")
  }

  @NeedsIndex.ForStandardLibrary
  void testSee0() {
    configureByFile("See0.java")
    myFixture.assertPreferredCompletionItems(0, "foo", "clone", "equals", "hashCode")
  }

  @NeedsIndex.ForStandardLibrary
  void testSee1() {
    configureByFile("See1.java")
    assertStringItems("notify", "notifyAll")
  }

  @NeedsIndex.ForStandardLibrary
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
    return Objects.requireNonNull(myFixture.getLookupElementStrings())
  }

  void testSee4() {
    configureByFile("See4.java")

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("A", "B", "C")))
  }

  void testSee5() {
    configureByFile("See5.java")

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myName")))
  }

  @NeedsIndex.ForStandardLibrary
  void testIDEADEV10620() {
    configureByFile("IDEADEV10620.java")

    checkResultByFile("IDEADEV10620-after.java")
  }

  void testException0() {
    configureByFile("Exception0.java")
    assertStringItems("apiNote", "deprecated", "exception", "hidden", "implNote", "implSpec", "see", "serialData", "since", "throws")
  }

  void testException1() {
    configureByFile("Exception1.java")
    assertTrue(myItems.length > 18)
  }

  @NeedsIndex.ForStandardLibrary
  void testException2() {
    myFixture.configureByFile("Exception2.java")
    myFixture.complete(CompletionType.SMART)
    assertStringItems("IllegalStateException", "IOException")
  }

  void testInlineLookup() {
    configureByFile("InlineTagName.java")
    assertStringItems("code", "docRoot", "index", "inheritDoc", "link", "linkplain", "literal", "snippet", "summary", "systemProperty", "value")
  }

  @NeedsIndex.ForStandardLibrary
  void testInlineLookup16() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_16, {configureByFile("InlineTagName16.java")})
    assertStringItems("code", "docRoot", "index", "inheritDoc", "link", "linkplain", "literal", "return", "summary", "systemProperty","value")
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

  @NeedsIndex.ForStandardLibrary
  void testShortenClassReference() throws Throwable {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testQualifiedClassReference() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    checkResultByFile(getTestName(false) + "_after.java")
  }

  @NeedsIndex.ForStandardLibrary
  void testQualifiedImportedClassReference() throws Throwable { doTest() }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.Full
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

  void "test suggest type param names"() {
    myFixture.configureByText "a.java", '''
/**
* @par<caret>
*/
class Foo<T,V>{}
'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'param', 'param <T>', 'param <V>'
    myFixture.type('\n <T>\n@para')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'param', 'param <V>'
  }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.Full
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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
      registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDocTag.class), provider)
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

  @NeedsIndex.ForStandardLibrary
  void "test insert link to class"() {
    myFixture.configureByText 'a.java', "/** FileNotFoEx<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@link java.io.FileNotFoundException<caret>} */"
  }

  @NeedsIndex.Full
  void "test insert link to inner class"() {
    myFixture.addClass('package zoo; public class Outer { public static class FooBarGoo{}}')
    myFixture.configureByText 'a.java', "/** FooBarGo<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@link zoo.Outer.FooBarGoo<caret>} */"
  }

  @NeedsIndex.ForStandardLibrary
  void "test insert link to imported class"() {
    myFixture.configureByText 'a.java', "import java.io.*; /** FileNotFoEx<caret> */ class A{}"
    myFixture.completeBasic()
    myFixture.checkResult "import java.io.*; /** {@link FileNotFoundException<caret>} */ class A{}"
  }

  void "test insert link to method"() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable())
    myFixture.configureByText 'a.java', "/** a. #fo<caret> */ interface Foo { void foo(int a); }}"
    myFixture.completeBasic()
    myFixture.type('\n')

    myFixture.checkResult "/** a. {@link #foo<selection>(int)<caret></selection>} */ interface Foo { void foo(int a); }}"
    assert TemplateManagerImpl.getTemplateState(myFixture.editor)

    myFixture.type('\t')
    myFixture.checkResult "/** a. {@link #foo(int)}<caret> */ interface Foo { void foo(int a); }}"
    assert !TemplateManagerImpl.getTemplateState(myFixture.editor)
  }

  @NeedsIndex.ForStandardLibrary
  void "test insert link to method in a q-named class"() {
    myFixture.configureByText 'a.java', "/** a. java.io.File#liFi<caret> */ interface Foo {}"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult "import java.io.File;\n\n/** a. {@link File#listFiles()} */ interface Foo {}"
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

  void "test no link inside code tag"() {
    myFixture.configureByText 'a.java', "/** {@code FBG<caret>} */ interface FooBarGoo {}"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@code FooBarGoo<caret>} */ interface FooBarGoo {}"
  }

  void "test completing inside qualified name"() {
    myFixture.configureByText 'a.java', "/** @see java.io.<caret> */"
    myFixture.completeBasic()
    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == 'IOException' }
    myFixture.type('\n')
    myFixture.checkResult "/** @see java.io.IOException<caret> */"
  }

  @NeedsIndex.ForStandardLibrary
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

  void "test allow to easily omit method parameters"() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable())
    myFixture.configureByText 'a.java', "/** {@link #fo<caret>} */ interface Foo { void foo(int a); }}"
    myFixture.completeBasic()

    myFixture.checkResult "/** {@link #foo<selection>(int)<caret></selection>} */ interface Foo { void foo(int a); }}"
    assert TemplateManagerImpl.getTemplateState(myFixture.editor)

    myFixture.type('\b\n')
    myFixture.checkResult "/** {@link #foo}<caret> */ interface Foo { void foo(int a); }}"
    assert !TemplateManagerImpl.getTemplateState(myFixture.editor)
  }

  void "test tags at top level"() {
    myFixture.configureByText 'a.java', "interface Foo { /**\n * <caret> */void foo(int a); }"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['@apiNote', '@deprecated', '@exception', '@hidden', '@implNote', '@implSpec', '@param', '@param a', '@see', '@serialData', '@since', '@throws']
    def element = myFixture.lookupElements[6]
    assert element.lookupString == "@param"
    selectItem(element)
    myFixture.checkResult("interface Foo { /**\n" +
                          " * @param  */void foo(int a); }")
  }

  void "test tags at top level inline"() {
    myFixture.configureByText 'a.java', "interface Foo { /** Hello <caret> */void foo(int a); }"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['{@code}', '{@docRoot}', '{@index}', '{@inheritDoc}', '{@linkplain}', '{@link}', '{@literal}', '{@snippet}', '{@summary}', '{@systemProperty}', '{@value}']
    def element = myFixture.lookupElements[5]
    assert element.lookupString == "{@link}"
    selectItem(element)
    myFixture.checkResult("interface Foo { /** Hello {@link <caret>} */void foo(int a); }")
  }

  void "test tags after return"() {
    myFixture.configureByText 'a.java', "interface Foo { /** @return <caret> */int foo(int a); }"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['{@code}', '{@docRoot}', '{@index}', '{@inheritDoc}', '{@linkplain}', '{@link}', '{@literal}', '{@return}', '{@snippet}', '{@summary}', '{@systemProperty}', '{@value}']
    def element = myFixture.lookupElements[5]
    assert element.lookupString == "{@link}"
    selectItem(element)
    myFixture.checkResult("interface Foo { /** @return {@link <caret>} */int foo(int a); }")
  }

  void "test tags at top level inline in brace"() {
    myFixture.configureByText 'a.java', "interface Foo { /** Hello {<caret>} */void foo(int a); }"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['@code', '@docRoot', '@index', '@inheritDoc', '@link', '@linkplain', '@literal', '@snippet', '@summary', '@systemProperty', '@value']
    def element = myFixture.lookupElements[4]
    assert element.lookupString == "@link"
    selectItem(element)
    myFixture.checkResult("interface Foo { /** Hello {@link <caret>} */void foo(int a); }")
  }

  void "test autoinsert link with space"() {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    try {
      myFixture.configureByText 'a.java', "/** {@li<caret>} */public class JavadocLink {}"
      myFixture.completeBasic()
      myFixture.type(' ')
      myFixture.checkResult("/** {@link <caret>} */public class JavadocLink {}")
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old
    }
  }

  void "test custom tag"() {
    def inspection = new JavadocDeclarationInspection()
    inspection.registerAdditionalTag("foobar")
    myFixture.enableInspections(inspection)
    myFixture.configureByText "a.java", "/**\n * @fo<caret>\n */\npublic class Demo {}"
    myFixture.completeBasic()
    myFixture.checkResult("/**\n * @foobar \n */\npublic class Demo {}")
  }
  
  void "test in snippet file"() {
    myFixture.addFileToProject("snippet-files/test.txt", "empty")
    myFixture.addFileToProject("snippet-files/sub/test.txt", "empty")
    myFixture.addFileToProject("snippet-files/sub/Test.java", "empty")
    myFixture.configureByText "a.java", "/**\n * {@snippet file=\"<caret>\"}\n */\npublic class Demo {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['test.txt', 'sub/']
    myFixture.lookup.setCurrentItem(myFixture.lookupElements[1])
    myFixture.type('\n')
    myFixture.checkResult("/**\n * {@snippet file=\"sub/<caret>\"}\n */\npublic class Demo {}")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['Test.java', 'test.txt']
    myFixture.type('\n')
    myFixture.checkResult("/**\n * {@snippet file=\"sub/Test.java<caret>\"}\n */\npublic class Demo {}")
  }
  
  void "test in snippet class"() {
    myFixture.addFileToProject("snippet-files/test.txt", "empty")
    myFixture.addFileToProject("snippet-files/sub/test.txt", "empty")
    myFixture.addFileToProject("snippet-files/sub/Test.java", "empty")
    myFixture.configureByText "a.java", "/**\n * {@snippet class=\"<caret>\"}\n */\npublic class Demo {}"
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['sub.']
    myFixture.type('\n')
    myFixture.checkResult("/**\n * {@snippet class=\"sub.<caret>\"}\n */\npublic class Demo {}")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['Test']
    myFixture.type('\n')
    myFixture.checkResult("/**\n * {@snippet class=\"sub.Test\"}\n */\npublic class Demo {}")
  }
  
  void testInSnippetAttribute() {
    myFixture.configureByText "a.java", "/**\n * {@snippet cl<caret>}\n */\npublic class Demo {}"
    myFixture.complete(CompletionType.BASIC, 0)
    myFixture.checkResult("/**\n * {@snippet class=<caret>}\n */\npublic class Demo {}")
  }
  
  void testInSnippetAttribute2() {
    myFixture.configureByText "a.java", "/**\n * {@snippet cl<caret>=x}\n */\npublic class Demo {}"
    myFixture.complete(CompletionType.BASIC, 0)
    myFixture.checkResult("/**\n * {@snippet class=<caret>x}\n */\npublic class Demo {}")
  }
  
  void testInSnippetAttribute3() {
    myFixture.configureByText "a.java", "/**\n * {@snippet class=X cl<caret>}\n */\npublic class Demo {}"
    myFixture.complete(CompletionType.BASIC, 0)
    myFixture.checkResult("/**\n * {@snippet class=X cl<caret>}\n */\npublic class Demo {}")
  }
  
  void testRegionCompletion() {
    myFixture.configureByText "a.java", """
    /**
     * {@snippet region=Re<caret>:
     *   // @start region="Reg1"
     *   // @end
     *   // @replace region="Region two"
     *   // @end
     *   // @highlight region="Reg#3"
     *   // @end
     * }
     */
    public class X {}
"""
    myFixture.complete(CompletionType.BASIC, 0)
    assert myFixture.lookupElementStrings == ['"Reg#3"', '"Region two"', 'Reg1']
    myFixture.type('\n')
    myFixture.checkResult("""
    /**
     * {@snippet region="Reg#3":
     *   // @start region="Reg1"
     *   // @end
     *   // @replace region="Region two"
     *   // @end
     *   // @highlight region="Reg#3"
     *   // @end
     * }
     */
    public class X {}
""")
  }
  
  void testRegionCompletionInQuotes() {
    myFixture.configureByText "a.java", """
    /**
     * {@snippet region="Re<caret>":
     *   // @start region="Reg1"
     *   // @end
     *   // @replace region="Region two"
     *   // @end
     *   // @highlight region="Reg#3"
     *   // @end
     * }
     */
    public class X {}
"""
    myFixture.complete(CompletionType.BASIC, 0)
    assert myFixture.lookupElementStrings == ['Reg#3', 'Reg1', 'Region two']
  }
  
  void testLangCompletion() {
    myFixture.configureByText "a.java", "/**\n * {@snippet lang=j<caret>}\n */\npublic class X {}\n"
    myFixture.complete(CompletionType.BASIC, 0)
    myFixture.lookupElementStrings.containsAll(['java', '"JSON Lines"', 'JSON'])
  }
}
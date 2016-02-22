package com.intellij.codeInsight.completion
import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection
import com.intellij.lang.StdLanguages
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
public class JavadocCompletionTest extends LightFixtureCompletionTestCase {
  private CodeStyleSettings settings
  private JavaCodeStyleSettings javaSettings
  
  @Override
  protected void tearDown() throws Exception {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    super.tearDown()
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/javadoc/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    settings = CodeStyleSettingsManager.getSettings(getProject());
    javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class)
    myFixture.enableInspections(new JavaDocLocalInspection());
  }

  public void testNamesInPackage() throws Exception {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("package-info.java", "p/package-info.java"));
    complete();
    assertStringItems("author", 'author ' + SystemProperties.getUserName(), "deprecated", "see", "since", "version")
  }

  public void testNamesInClass() throws Exception {
    configureByFile("ClassTagName.java");
    assertStringItems("author", 'author ' + SystemProperties.getUserName(), "deprecated", "param", "see", "serial", "since", "version");
  }

  public void testNamesInField() throws Exception {
    configureByFile("FieldTagName.java");
    assertStringItems("deprecated", "see", "serial", "serialField", "since");
  }

  public void testNamesInMethod0() throws Exception {
    configureByFile("MethodTagName0.java");
    assertStringItems("deprecated", "exception", "param", "return", "see", "serialData", "since", "throws");
  }

  public void testNamesInMethod1() throws Exception {
    configureByFile("MethodTagName1.java");
    assertStringItems("see", "serialData", "since", "throws");
  }

  public void testParamValueCompletion() throws Exception {
    configureByFile("ParamValue0.java");
    assertStringItems("a", "b", "c");
  }

  public void testParamValueWithPrefixCompletion() throws Exception {
    configureByFile("ParamValue1.java");
    assertStringItems("a1", "a2", "a3");
  }

  public void testDescribedParameters() throws Exception {
    configureByFile("ParamValue2.java");
    assertStringItems("a2", "a3");
  }

  public void testSee0() throws Exception {
    configureByFile("See0.java");
    myFixture.assertPreferredCompletionItems(0, "foo", "clone", "equals", "hashCode");
  }

  public void testSee1() throws Exception {
    configureByFile("See1.java");
    assertStringItems("notify", "notifyAll");
  }

  public void testSee2() throws Exception {
    configureByFile("See2.java");
    assertStringItems("notify", "notifyAll");
  }

  public void testSee3() throws Exception {
    configureByFile("See3.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myField")));
  }

  @NotNull
  private List<String> getLookupElementStrings() {
    return ObjectUtils.assertNotNull(myFixture.getLookupElementStrings());
  }

  public void testSee4() throws Exception {
    configureByFile("See4.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("A", "B", "C")));
  }

  public void testSee5() throws Exception {
    configureByFile("See5.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myName")));
  }

  public void testIDEADEV10620() throws Exception {
    configureByFile("IDEADEV10620.java");

    checkResultByFile("IDEADEV10620-after.java");
  }

  public void testException0() throws Exception {
    configureByFile("Exception0.java");
    assertStringItems("deprecated", "exception", "param", "see", "serialData", "since", "throws");
  }

  public void testException1() throws Exception {
    configureByFile("Exception1.java");
    assertTrue(myItems.length > 18);
  }

  public void testException2() throws Exception {
    myFixture.configureByFile("Exception2.java");
    myFixture.complete(CompletionType.SMART);
    assertStringItems("IllegalStateException", "IOException");
  }

  public void testInlineLookup() throws Exception {
    configureByFile("InlineTagName.java");
    assertStringItems("code", "docRoot", "inheritDoc", "link", "linkplain", "literal", "value");
  }

  public void testFinishWithSharp() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      checkFinishWithSharp();
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  private void checkFinishWithSharp() throws Exception {
    myFixture.configureByFile("FinishWithSharp.java");
    myFixture.completeBasic();
    type('#');
    checkResultByFile("FinishWithSharp_after.java");
    final List<LookupElement> items = getLookup().getItems();
    assertEquals("bar", items.get(0).getLookupString());
    assertEquals("foo", items.get(1).getLookupString());
  }

  public void testShortenClassName() throws Throwable {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    doTest();
  }

  public void testMethodBeforeSharp() throws Throwable {
    doTest();
  }

  public void testFieldReferenceInInnerClassJavadoc() throws Throwable {
    doTest();
  }

  public void testShortenClassReference() throws Throwable {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    doTest()
  }
  public void testQualifiedClassReference() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testQualifiedImportedClassReference() throws Throwable { doTest() }

  public void testThrowsNonImported() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTest() throws Exception {
    configureByFile(getTestName(false) + ".java");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testInlinePackageReferenceCompletion() throws Exception {
    configureByFile("InlineReference.java");
    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("io", "lang", "util")));
  }

  public void testQualifyClassReferenceInPackageStatement() throws Exception {
    configureByFile(getTestName(false) + ".java");
    myFixture.type('\n');
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void "test suggest param names"() {
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

  public void "test fqns in package info"() {
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

  public void "test suggest same param descriptions"() {
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
    myFixture.assertPreferredCompletionItems 0, 'some', 'some integer param'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
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

  public void "test see super class"() {
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

  public void testShortenMethodParameterTypes() {
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

  public void testNoMethodsAfterClassDot() {
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
  
  public void testShortNameInJavadocIfWasImported() {
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

  public void testFqnInJavadocIfWasNotImported() {
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


  public void testFqnNameInJavadocIfWasImported() {
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

  public void testShortNameInJavadoc() {
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

  public void testShortNameInJavadocIfWasImportOnDemand() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    def text = '''
import java.util.*;

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
import java.util.*;

/**
 * {@link Map}
 */
class Test {
}
'''
  }

  public void testNullQualifiedName() {
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
  
  public void testShortNameIfImplicitlyImported() {
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

  public void testShortNameIfInnerClass() {
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

  public void testCustomReferenceProvider() throws Exception {
    PsiReferenceRegistrarImpl registrar =
      (PsiReferenceRegistrarImpl) ReferenceProvidersRegistry.getInstance().getRegistrar(StdLanguages.JAVA);
    PsiReferenceProvider provider = new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
        def ref = new PsiReferenceBase<PsiElement>(element) {

          @Override
          public PsiElement resolve() {
            return element;
          }

          @Override
          @NotNull
          public Object[] getVariants() {
            return ["1", "2", "3"]
          }
        }
        return [ref]
      }
    };
    try {
      registrar.registerReferenceProvider(PsiDocTag.class, provider);
      configureByFile("ReferenceProvider.java");
      assertStringItems("1", "2", "3");
    }
    finally {
      registrar.unregisterReferenceProvider(PsiDocTag.class, provider);
    }
  }

  public void "test complete author name"() {
    def userName = SystemProperties.userName
    assert userName
    myFixture.configureByText 'a.java', "/** @author <caret> */"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult "/** @author $userName<caret> */"
  }

  public void "test insert link to class"() {
    myFixture.configureByText 'a.java', "/** FileNotFoEx<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@link java.io.FileNotFoundException<caret>} */"
  }

  public void "test insert link to inner class"() {
    myFixture.addClass('package zoo; public class Outer { public static class FooBarGoo{}}')
    myFixture.configureByText 'a.java', "/** FooBarGo<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@link zoo.Outer#FooBarGoo<caret>} */"
  }

  public void "test insert link to imported class"() {
    myFixture.configureByText 'a.java', "import java.io.*; /** FileNotFoEx<caret> */ class A{}"
    myFixture.completeBasic()
    myFixture.checkResult "import java.io.*; /** {@link FileNotFoundException<caret>} */ class A{}"
  }

  public void "test insert link to method"() {
    myFixture.configureByText 'a.java', "/** a. #fo<caret> */ interface Foo { void foo(int a); }}"
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult "/** a. {@link #foo(int)}<caret> */ interface Foo { void foo(int a); }}"
  }

  public void "test wrap null into code tag"() {
    myFixture.configureByText 'a.java', "/** nul<caret> */"
    myFixture.completeBasic()
    myFixture.checkResult "/** {@code null}<caret> */"
  }
}

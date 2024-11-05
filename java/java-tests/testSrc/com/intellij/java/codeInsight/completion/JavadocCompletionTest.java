package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("JavadocDeclaration")
public class JavadocCompletionTest extends LightFixtureCompletionTestCase {
  private JavaCodeStyleSettings javaSettings;

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/javadoc/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    myFixture.enableInspections(new JavadocDeclarationInspection());
  }

  public void testNamesInPackage() {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("package-info.java", "p/package-info.java"));
    complete();
    assertStringItems("author", "author " + SystemProperties.getUserName(), "deprecated", "see", "since", "version");
  }

  public void testNamesInClass() {
    configureByFile("ClassTagName.java");
    assertStringItems("apiNote", "author", "author " + SystemProperties.getUserName(), "deprecated", "hidden", "implNote", "implSpec", "see", "serial", "since", "version");
  }

  public void testNamesInField() {
    configureByFile("FieldTagName.java");
    assertStringItems("apiNote", "deprecated", "hidden", "implNote", "implSpec", "see", "serial", "serialField", "since");
  }

  public void testNamesInMethod0() {
    configureByFile("MethodTagName0.java");
    assertStringItems("apiNote", "deprecated", "exception", "hidden", "implNote", "implSpec", "return", "see", "serialData", "since", "throws");
  }

  public void testNamesInMethod1() {
    configureByFile("MethodTagName1.java");
    assertStringItems("see", "serialData", "since", "implSpec", "throws");
  }

  public void testParamValueCompletion() {
    configureByFile("ParamValue0.java");
    assertStringItems("a", "b", "c", "<A>", "<B>");
  }

  public void testParamValueWithPrefixCompletion() {
    configureByFile("ParamValue1.java");
    assertStringItems("a1", "a2", "a3");
  }

  public void testTypeParamValueWithPrefix() {
    configureByTestName();
    assertStringItems("<A>", "<B>");
  }

  public void testDescribedParameters() {
    configureByFile("ParamValue2.java");
    assertStringItems("a2", "a3");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSee0() {
    configureByFile("See0.java");
    myFixture.assertPreferredCompletionItems(0, "foo", "clone", "equals", "hashCode");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSee1() {
    configureByFile("See1.java");
    assertStringItems("notify", "notifyAll");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSee2() {
    configureByFile("See2.java");
    assertStringItems("notify", "notifyAll");
  }

  public void testSee3() {
    configureByFile("See3.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myField")));
  }

  @NotNull
  private List<String> getLookupElementStrings() {
    return Objects.requireNonNull(myFixture.getLookupElementStrings());
  }

  public void testSee4() {
    configureByFile("See4.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("A", "B", "C")));
  }

  public void testSee5() {
    configureByFile("See5.java");

    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myName")));
  }

  @NeedsIndex.ForStandardLibrary
  public void testIDEADEV10620() {
    configureByFile("IDEADEV10620.java");

    checkResultByFile("IDEADEV10620-after.java");
  }

  public void testException0() {
    configureByFile("Exception0.java");
    assertStringItems("apiNote", "deprecated", "exception", "hidden", "implNote", "implSpec", "see", "serialData", "since", "throws");
  }

  public void testException1() {
    configureByFile("Exception1.java");
    assertTrue(myItems.length > 18);
  }

  @NeedsIndex.ForStandardLibrary
  public void testException2() {
    myFixture.configureByFile("Exception2.java");
    myFixture.complete(CompletionType.SMART);
    assertStringItems("IllegalStateException", "IOException");
  }

  public void testInlineLookup() {
    configureByFile("InlineTagName.java");
    assertStringItems("code", "docRoot", "index", "inheritDoc", "link", "linkplain", "literal", "snippet", "summary", "systemProperty", "value");
  }

  @NeedsIndex.ForStandardLibrary
  public void testInlineLookup16() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, () -> {
      configureByFile("InlineTagName16.java");
      assertStringItems("code", "docRoot", "index", "inheritDoc", "link", "linkplain", "literal", "return", "summary", "systemProperty", "value");
    });
  }

  public void testFinishWithSharp() {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      checkFinishWithSharp();
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  private void checkFinishWithSharp() {
    myFixture.configureByFile("FinishWithSharp.java");
    myFixture.completeBasic();
    type("#");
    checkResultByFile("FinishWithSharp_after.java");
    final List<LookupElement> items = getLookup().getItems();
    assertEquals("bar", items.get(0).getLookupString());
    assertEquals("foo", items.get(1).getLookupString());
  }

  public void testShortenClassName() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    doTest();
  }

  public void testMethodBeforeSharp() {
    doTest();
  }
  public void testMethodInMarkdownReferenceLink() {
    doTest();
  }

  public void testFieldReferenceInInnerClassJavadoc() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testShortenClassReference() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testQualifiedClassReference() {
    configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testQualifiedImportedClassReference() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testThrowsNonImported() {
    configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTest() {
    configureByFile(getTestName(false) + ".java");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testInlinePackageReferenceCompletion() {
    configureByFile("InlineReference.java");
    assertTrue(getLookupElementStrings().containsAll(Arrays.asList("io", "lang", "util")));
  }

  @NeedsIndex.Full
  public void testQualifyClassReferenceInPackageStatement() {
    configureByFile(getTestName(false) + ".java");
    myFixture.type("\n");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void test_suggest_param_names() {
    myFixture.configureByText("a.java", """
      class Foo {
        /**
        * @par<caret>
        */
        void foo(int intParam, Object param2) {
        }
      }""");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "param", "param intParam", "param param2");
    myFixture.type("\n intParam\n@para");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "param", "param param2");
  }

  public void test_suggest_type_param_names() {
    myFixture.configureByText("a.java", """
      /**
      * @par<caret>
      */
      class Foo<T,V>{}""");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "param", "param <T>", "param <V>");
    myFixture.type("\n <T>\n@para");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "param", "param <V>");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_fqns_in_package_info() {
    myFixture.configureByText("package-info.java", """
      /**
       * {@link java.util.Map#putA<caret>}
       */
      """);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResult("""
                            /**
                             * {@link java.util.Map#putAll(java.util.Map)}
                             */
                            """);
  }

  public void test_suggest_same_param_descriptions() {
    myFixture.configureByText("a.java", """
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
      """);
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "some integer param", "some");
    myFixture.type("\t");
    myFixture.checkResult("""
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
                            """);
  }

  public void test_suggest_same_param_descriptions_with_no_text_after_param_name() {
    myFixture.configureByText("a.java", """
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
      """);
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "some integer param");
  }

  @NeedsIndex.Full
  public void test_see_super_class() {
    myFixture.addClass("package foo; public interface Foo {}");
    myFixture.addClass("package bar; public class Bar {} ");
    myFixture.configureByText("a.java", """
      import foo.*;
      import bar.*;
      
      /**
       * @se<caret>
       */
      class Impl extends Bar implements Foo {}
      """);
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "see", "see bar.Bar", "see foo.Foo");
  }

  public void testShortenMethodParameterTypes() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    myFixture.addClass("package foo; public class Foo {}");
    myFixture.addClass("package bar; public class Bar {}");
    myFixture.configureByText("a.java", """
      import foo.*;
      import bar.*;
      
      /**
      * {@link #go<caret>
      */
      class Goo { void goo(Foo foo, Bar bar) {} }
      """);
    myFixture.completeBasic();
    assert myFixture.getEditor().getDocument().getText().contains("@link #goo(Foo, Bar)");
  }

  public void testNoMethodsAfterClassDot() {
    String text = """
      /**
      * @see java.util.List.<caret>
      */
      class Goo { void goo(Foo foo, Bar bar) {} }
      """;
    myFixture.configureByText("a.java", text);
    assertEquals(0, myFixture.completeBasic().length);
    myFixture.checkResult(text);
  }

  @NeedsIndex.ForStandardLibrary
  public void testShortNameInJavadocIfWasImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      import java.util.Map;

      /**
       * {@link Ma<caret>}
       */
      class Test {
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
                            import java.util.Map;

                            /**
                             * {@link Map}
                             */
                            class Test {
                            }
                            """);
  }

  @NeedsIndex.ForStandardLibrary
  public void testFqnInJavadocIfWasNotImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      import java.util.Map;

      /**
       * {@link HashMa<caret>}
       */
      class Test {
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
                            import java.util.Map;

                            /**
                             * {@link java.util.HashMap}
                             */
                            class Test {
                            }
                            """);
  }

  @NeedsIndex.ForStandardLibrary
  public void testFqnNameInJavadocIfWasImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
    String text = """
      import java.util.Map;
      
      /**
       * {@link Ma<caret>}
       */
      class Test {
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
                            import java.util.Map;
                            
                            /**
                             * {@link java.util.Map}
                             */
                            class Test {
                            }
                            """);
  }

  @NeedsIndex.ForStandardLibrary
  public void testShortNameInJavadoc() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    String text = """
      import java.util.Map;

      /**
       * {@link Ma<caret>}
       */
      class Test {
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
                            import java.util.Map;

                            /**
                             * {@link Map}
                             */
                            class Test {
                            }
                            """);
  }

  @NeedsIndex.ForStandardLibrary
  public void testShortNameInJavadocIfWasImportOnDemand() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      import java.util.*;
      
      /**
       * {@link ArraLi<caret>}
       */
      class Test {
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
                            import java.util.*;
                            
                            /**
                             * {@link ArrayList}
                             */
                            class Test {
                            }
                            """);
  }

  public void testNullQualifiedName() {
    @SuppressWarnings("DanglingJavadoc") String text = """
      public final class Test {

        public static void main(String[] args) {
          class Super {
          }

          /**
           * {@link Su<caret>}
           */
          Super aSuper = new Super();

        }
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
      public final class Test {

        public static void main(String[] args) {
          class Super {
          }

          /**
           * {@link Super}
           */
          Super aSuper = new Super();

        }
      }
      """);

  }

  @NeedsIndex.ForStandardLibrary
  public void testShortNameIfImplicitlyImported() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      /**
       * {@link Str<caret>}
       */
      class Test {
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
      /**
       * {@link String}
       */
      class Test {
      }
      """);
  }

  public void testShortNameIfInnerClass() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      package pkg;

      class Foo {

          /**
           * @throws FooE<caret>
           */
          void foo() {
          }

          static class FooException extends RuntimeException {}
      }
      """;
    myFixture.configureByText("a.java", text);
    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
      package pkg;

      class Foo {

          /**
           * @throws FooException\s
           */
          void foo() {
          }

          static class FooException extends RuntimeException {}
      }
      """);
  }

  public void testCustomReferenceProvider() {
    PsiReferenceRegistrarImpl registrar =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE);
    PsiReferenceProvider provider = new PsiReferenceProvider() {
      @Override
      public @NotNull PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element,
                                                                      @NotNull final ProcessingContext context) {
        PsiReferenceBase<PsiElement> ref = new PsiReferenceBase<>(element) {
          @Override
          public PsiElement resolve() {
            return element;
          }

          @Override
          public @NotNull Object @NotNull [] getVariants() {
            return new String[]{"1", "2", "3"};
          }
        };
        return new PsiReferenceBase[]{ref};
      }
    };
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDocTag.class), provider,
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY, getTestRootDisposable());
    configureByFile("ReferenceProvider.java");
    assertStringItems("1", "2", "3");
  }

  public void test_complete_author_name() {
    String userName = SystemProperties.getUserName();
    assertNotNull(userName);
    myFixture.configureByText("a.java", "/** @author <caret> */");
    myFixture.completeBasic();
    myFixture.type("\n");
    myFixture.checkResult("/** @author " + userName + "<caret> */");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_insert_link_to_class() {
    myFixture.configureByText("a.java", "/** FileNotFoEx<caret> */");
    myFixture.completeBasic();
    myFixture.checkResult("/** {@link java.io.FileNotFoundException<caret>} */");
  }

  @NeedsIndex.Full
  public void test_insert_link_to_inner_class() {
    myFixture.addClass("package zoo; public class Outer { public static class FooBarGoo{}}");
    myFixture.configureByText("a.java", "/** FooBarGo<caret> */");
    myFixture.completeBasic();
    myFixture.checkResult("/** {@link zoo.Outer.FooBarGoo<caret>} */");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_insert_link_to_imported_class() {
    myFixture.configureByText("a.java", "import java.io.*; /** FileNotFoEx<caret> */ class A{}");
    myFixture.completeBasic();
    myFixture.checkResult("import java.io.*; /** {@link FileNotFoundException<caret>} */ class A{}");
  }

  public void test_insert_link_to_method() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "/** a. #fo<caret> */ interface Foo { void foo(int a); }}");
    myFixture.completeBasic();
    myFixture.type("\n");

    myFixture.checkResult("/** a. {@link #foo<selection>(int)<caret></selection>} */ interface Foo { void foo(int a); }}");
    assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));

    myFixture.type("\t");
    myFixture.checkResult("/** a. {@link #foo(int)}<caret> */ interface Foo { void foo(int a); }}");
    assertNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
  }

  @NeedsIndex.ForStandardLibrary
  public void test_insert_link_to_method_in_a_q_named_class() {
    myFixture.configureByText("a.java", "/** a. java.io.File#liFi<caret> */ interface Foo {}");
    myFixture.completeBasic();
    myFixture.type("\n");
    myFixture.checkResult("import java.io.File;\n\n/** a. {@link File#listFiles()} */ interface Foo {}");
  }

  public void test_insert_link_to_field() {
    myFixture.configureByText("a.java", "/** a. #fo<caret> */ interface Foo { int foo; }}");
    myFixture.completeBasic();
    myFixture.checkResult("/** a. {@link #foo}<caret> */ interface Foo { int foo; }}");
  }

  public void test_wrap_null_into_code_tag() {
    myFixture.configureByText("a.java", "/** nul<caret> */");
    myFixture.completeBasic();
    myFixture.checkResult("/** {@code null}<caret> */");
  }

  public void test_null_inside_code_tag() {
    myFixture.configureByText("a.java", "/** {@code nul<caret>} */");
    myFixture.completeBasic();
    myFixture.checkResult("/** {@code null<caret>} */");
  }

  public void test_no_link_inside_code_tag() {
    myFixture.configureByText("a.java", "/** {@code FBG<caret>} */ interface FooBarGoo {}");
    myFixture.completeBasic();
    myFixture.checkResult("/** {@code FooBarGoo<caret>} */ interface FooBarGoo {}");
  }

  public void test_completing_inside_qualified_name() {
    myFixture.configureByText("a.java", "/** @see java.io.<caret> */");
    myFixture.completeBasic();
    myFixture.getLookup()
      .setCurrentItem(ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("IOException")));
    myFixture.type("\n");
    myFixture.checkResult("/** @see java.io.IOException<caret> */");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_no_hierarchical_generic_method_duplicates() {
    myFixture.configureByText("a.java", """
      interface Foo<T> {
          void foo(T t);
      }
      
      /**
       * {@link Bar#f<caret>}\s
       */
      interface Bar<T> extends Foo<T> {
          @Override
          void foo(T t);
      }""");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "foo", "finalize");
  }

  public void test_allow_to_easily_omit_method_parameters() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "/** {@link #fo<caret>} */ interface Foo { void foo(int a); }}");
    myFixture.completeBasic();

    myFixture.checkResult("/** {@link #foo<selection>(int)<caret></selection>} */ interface Foo { void foo(int a); }}");
    assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));

    myFixture.type("\b\n");
    myFixture.checkResult("/** {@link #foo}<caret> */ interface Foo { void foo(int a); }}");
    assertNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
  }

  public void test_tags_at_top_level() {
    myFixture.configureByText("a.java", "interface Foo { /**\n * <caret> */void foo(int a); }");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("@apiNote", "@deprecated", "@exception", "@hidden", "@implNote", "@implSpec", "@param", "@param a", "@see",
                               "@serialData", "@since", "@throws"),
                 myFixture.getLookupElementStrings());
    LookupElement element = myFixture.getLookupElements()[6];
    assertEquals("@param", element.getLookupString());
    selectItem(element);
    myFixture.checkResult("interface Foo { /**\n" +
                          " * @param  */void foo(int a); }");
  }

  public void test_tags_at_top_level_inline() {
    myFixture.configureByText("a.java", "interface Foo { /** Hello <caret> */void foo(int a); }");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("{@code}", "{@docRoot}", "{@index}", "{@inheritDoc}", "{@linkplain}", "{@link}", "{@literal}", "{@snippet}", "{@summary}", "{@systemProperty}", "{@value}"),
                 myFixture.getLookupElementStrings());
    LookupElement element = myFixture.getLookupElements()[5];
    assertEquals("{@link}", element.getLookupString());
    selectItem(element);
    myFixture.checkResult("interface Foo { /** Hello {@link <caret>} */void foo(int a); }");
  }

  public void test_tags_after_return() {
    myFixture.configureByText("a.java", "interface Foo { /** @return <caret> */int foo(int a); }");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("{@code}", "{@docRoot}", "{@index}", "{@inheritDoc}", "{@linkplain}", "{@link}", "{@literal}", "{@return}",
                               "{@snippet}", "{@summary}", "{@systemProperty}", "{@value}"), myFixture.getLookupElementStrings());
    LookupElement element = myFixture.getLookupElements()[5];
    assert element.getLookupString().equals("{@link}");
    selectItem(element);
    myFixture.checkResult("interface Foo { /** @return {@link <caret>} */int foo(int a); }");
  }

  public void test_tags_at_top_level_inline_in_brace() {
    myFixture.configureByText("a.java", "interface Foo { /** Hello {<caret>} */void foo(int a); }");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("@code", "@docRoot", "@index", "@inheritDoc", "@link", "@linkplain", "@literal", "@snippet", "@summary",
                     "@systemProperty", "@value"), myFixture.getLookupElementStrings());
    LookupElement element = myFixture.getLookupElements()[4];
    assert element.getLookupString().equals("@link");
    selectItem(element);
    myFixture.checkResult("interface Foo { /** Hello {@link <caret>} */void foo(int a); }");
  }

  public void test_autoinsert_link_with_space() {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      myFixture.configureByText("a.java", "/** {@li<caret>} */public class JavadocLink {}");
      myFixture.completeBasic();
      myFixture.type(" ");
      myFixture.checkResult("/** {@link <caret>} */public class JavadocLink {}");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  public void test_custom_tag() {
    var inspection = new JavadocDeclarationInspection();
    inspection.registerAdditionalTag("foobar");
    myFixture.enableInspections(inspection);
    myFixture.configureByText("a.java", "/**\n * @fo<caret>\n */\npublic class Demo {}");
    myFixture.completeBasic();
    myFixture.checkResult("/**\n * @foobar \n */\npublic class Demo {}");
  }

  public void test_in_snippet_file() {
    myFixture.addFileToProject("snippet-files/test.txt", "empty");
    myFixture.addFileToProject("snippet-files/sub/test.txt", "empty");
    myFixture.addFileToProject("snippet-files/sub/Test.java", "empty");
    myFixture.configureByText("a.java", "/**\n * {@snippet file=\"<caret>\"}\n */\npublic class Demo {}");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("test.txt", "sub/"), myFixture.getLookupElementStrings());
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
    myFixture.type("\n");
    myFixture.checkResult("/**\n * {@snippet file=\"sub/<caret>\"}\n */\npublic class Demo {}");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("Test.java", "test.txt"), myFixture.getLookupElementStrings());
    myFixture.type("\n");
    myFixture.checkResult("/**\n * {@snippet file=\"sub/Test.java<caret>\"}\n */\npublic class Demo {}");
  }

  public void test_in_snippet_class() {
    myFixture.addFileToProject("snippet-files/test.txt", "empty");
    myFixture.addFileToProject("snippet-files/sub/test.txt", "empty");
    myFixture.addFileToProject("snippet-files/sub/Test.java", "empty");
    myFixture.configureByText("a.java", "/**\n * {@snippet class=\"<caret>\"}\n */\npublic class Demo {}");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("sub."), myFixture.getLookupElementStrings());
    myFixture.type("\n");
    myFixture.checkResult("/**\n * {@snippet class=\"sub.<caret>\"}\n */\npublic class Demo {}");
    myFixture.completeBasic();
    assertEquals(Arrays.asList("Test"), myFixture.getLookupElementStrings());
    myFixture.type("\n");
    myFixture.checkResult("/**\n * {@snippet class=\"sub.Test\"}\n */\npublic class Demo {}");
  }

  public void testInSnippetAttribute() {
    myFixture.configureByText("a.java", "/**\n * {@snippet cl<caret>}\n */\npublic class Demo {}");
    myFixture.complete(CompletionType.BASIC, 0);
    myFixture.checkResult("/**\n * {@snippet class=<caret>}\n */\npublic class Demo {}");
  }

  public void testInSnippetAttribute2() {
    myFixture.configureByText("a.java", "/**\n * {@snippet cl<caret>=x}\n */\npublic class Demo {}");
    myFixture.complete(CompletionType.BASIC, 0);
    myFixture.checkResult("/**\n * {@snippet class=<caret>x}\n */\npublic class Demo {}");
  }

  public void testInSnippetAttribute3() {
    myFixture.configureByText("a.java", "/**\n * {@snippet class=X cl<caret>}\n */\npublic class Demo {}");
    myFixture.complete(CompletionType.BASIC, 0);
    myFixture.checkResult("/**\n * {@snippet class=X cl<caret>}\n */\npublic class Demo {}");
  }

  public void testRegionCompletion() {
    myFixture.configureByText("a.java", """
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
    public class X {}""");
    myFixture.complete(CompletionType.BASIC, 0);
    assertEquals(myFixture.getLookupElementStrings(), new ArrayList<>(
      Arrays.asList("\"Reg#3\"", "\"Region two\"", "Reg1")));
    myFixture.type("\n");
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
      public class X {}""");
  }

  public void testRegionCompletionInQuotes() {
    myFixture.configureByText("a.java", """
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
      """);
    myFixture.complete(CompletionType.BASIC, 0);
    assertEquals(Arrays.asList("Reg#3", "Reg1", "Region two"), myFixture.getLookupElementStrings());
  }

  public void testLangCompletion() {
    myFixture.configureByText("a.java", "/**\n * {@snippet lang=j<caret>}\n */\npublic class X {}\n");
    myFixture.complete(CompletionType.BASIC, 0);
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue(strings.containsAll(Arrays.asList("java", "JShellLanguage", "JVM")));
  }


}

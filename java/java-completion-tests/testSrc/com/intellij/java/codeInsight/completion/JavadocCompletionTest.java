// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("JavadocDeclaration")
@TestApplication
@IndexingModeTemplate
public class JavadocCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST);
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/javadoc/");

  private JavaCodeStyleSettings javaSettings;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      myFixture.enableInspections(new JavadocDeclarationInspection());
    });
  }

  @Test
  public void testNamesInPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("package-info.java", "p/package-info.java"));
      completion.get().complete();
      completion.get().assertStringItems("author", "author " + SystemProperties.getUserName(), "deprecated", "see", "since", "version");
    });
  }

  @Test
  public void testNamesInClass() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ClassTagName.java");
      completion.get().assertStringItems("apiNote", "author", "author " + SystemProperties.getUserName(), "deprecated", "hidden", "implNote", "implSpec", "see", "serial", "since", "version");
    });
  }

  @Test
  public void testNamesInField() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("FieldTagName.java");
      completion.get().assertStringItems("apiNote", "deprecated", "hidden", "implNote", "implSpec", "see", "serial", "serialField", "since");
    });
  }

  @Test
  public void testNamesInMethod0() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("MethodTagName0.java");
      completion.get().assertStringItems("apiNote", "deprecated", "exception", "hidden", "implNote", "implSpec", "return", "see", "serialData", "since", "throws");
    });
  }

  @Test
  public void testNamesInMethod1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("MethodTagName1.java");
      completion.get().assertStringItems("see", "serialData", "since", "implSpec", "throws");
    });
  }

  @Test
  public void testParamValueCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ParamValue0.java");
      completion.get().assertStringItems("a", "b", "c", "<A>", "<B>");
    });
  }

  @Test
  public void testParamValueWithPrefixCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ParamValue1.java");
      completion.get().assertStringItems("a1", "a2", "a3");
    });
  }

  @Test
  public void testTypeParamValueWithPrefix() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().assertStringItems("<A>", "<B>");
    });
  }

  @Test
  public void testDescribedParameters() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ParamValue2.java");
      completion.get().assertStringItems("a2", "a3");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSee0() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("See0.java");
      myFixture.assertPreferredCompletionItems(0, "foo", "clone", "equals", "hashCode");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSee1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("See1.java");
      completion.get().assertStringItems("notify", "notifyAll");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSee2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("See2.java");
      completion.get().assertStringItems("notify", "notifyAll");
    });
  }

  @Test
  public void testSee3() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("See3.java");

      assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myField")));
    });
  }

  @NotNull
  private List<String> getLookupElementStrings() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    return EdtTestUtil.runInEdtAndGet(() -> {
      return Objects.requireNonNull(myFixture.getLookupElementStrings());
    });
  }

  @Test
  public void testSee4() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("See4.java");

      assertTrue(getLookupElementStrings().containsAll(Arrays.asList("A", "B", "C")));
    });
  }

  @Test
  public void testSee5() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("See5.java");

      assertTrue(getLookupElementStrings().containsAll(Arrays.asList("foo", "myName")));
    });
  }

  @Test
  public void testSee7() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("See7.java");
      assertTrue(getLookupElementStrings().containsAll(Arrays.asList("my-id1", "my-id2", "my-id3", "my-id4")));
    });
  }

  @Test
  public void testFragmentCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        /**
         * <h1>Class Heading</h1>
         * <p id="class-id"></p>
         * {@systemProperty class.property}
         * {@index "index entry" description}
         * @see ##<caret>
         */
        class Test {
          /** <p id="field-id"></p> */
          int field;

          /// # Method Heading
          void method() {}
        }
        """);
      myFixture.completeBasic();
      assertTrue(getLookupElementStrings().containsAll(Arrays.asList(
        "class-heading-heading", "class-id", "class.property", "indexentry", "field-id", "method-heading-heading")));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPackageFragmentCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("p/package-info.java", """
        /**
         * <h1>Package Heading</h1>
         * <p id="package-id"></p>
         * {@systemProperty package.property}
         * {@index "package entry" description}
         */
        package p;
        """);
      myFixture.configureByText("Test.java", """
        /**
         * {@link p##<caret>}
         */
        class Test {}
        """);
      myFixture.completeBasic();
      assertTrue(getLookupElementStrings().containsAll(Arrays.asList(
        "package-heading-heading", "package-id", "package.property", "packageentry")));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testIDEADEV10620() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("IDEADEV10620.java");

      completion.get().checkResultByFile("IDEADEV10620-after.java");
    });
  }

  @Test
  public void testException0() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Exception0.java");
      completion.get().assertStringItems("apiNote", "deprecated", "exception", "hidden", "implNote", "implSpec", "see", "serialData", "since", "throws");
    });
  }

  @Test
  public void testException1() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("Exception1.java");
      assertTrue(completion.get().getItems().length > 18);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testException2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile("Exception2.java");
      myFixture.complete(CompletionType.SMART);
      completion.get().assertStringItems("IllegalStateException", "IOException");
    });
  }

  @Test
  public void testInlineLookup() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("InlineTagName.java");
      completion.get().assertStringItems("code", "docRoot", "index", "inheritDoc", "link", "linkplain", "literal", "snippet", "summary", "systemProperty", "value");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInlineLookup16() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_16, () -> {
        completion.get().configureByFile("InlineTagName16.java");
        completion.get().assertStringItems("code", "docRoot", "index", "inheritDoc", "link", "linkplain", "literal", "return", "summary", "systemProperty", "value");
      });
    });
  }

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile("FinishWithSharp.java");
      myFixture.completeBasic();
      completion.get().type("#");
      completion.get().checkResultByFile("FinishWithSharp_after.java");
      final List<LookupElement> items = completion.get().getLookup().getItems();
      assertEquals("bar", items.get(0).getLookupString());
      assertEquals("foo", items.get(1).getLookupString());
    });
  }

  @Test
  public void testShortenClassName() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    doTest();
  }

  @Test
  public void testMethodBeforeSharp() {
    doTest();
  }
  @Test
  public void testMethodInMarkdownReferenceLink() {
    doTest();
  }

  @Test
  public void testFieldReferenceInInnerClassJavadoc() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testShortenClassReference() {
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifiedClassReference() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifiedImportedClassReference() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testThrowsNonImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.BASIC, 2);
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testInlinePackageReferenceCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("InlineReference.java");
      assertTrue(getLookupElementStrings().containsAll(Arrays.asList("io", "lang", "util")));
    });
  }

  @Test
  public void test_suggest_param_names() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void test_suggest_type_param_names() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_fqns_in_package_info() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_7,  ()-> {
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
      });
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_fqns_in_package_info_modern_java() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("package-info.java", """
        /**
         * {@link java.util.Map#putA<caret>}
         */
        """);
      myFixture.complete(CompletionType.BASIC);
      myFixture.checkResult("""
                              /**
                               * {@link java.util.Map#putAll(Map)}
                               */
                            
                              import java.util.Map;""");
    });
  }

  @Test
  public void test_suggest_same_param_descriptions() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void test_suggest_same_param_descriptions_with_no_text_after_param_name() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.Full
  public void test_see_super_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testShortenMethodParameterTypes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testNoMethodsAfterClassDot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String text = """
      /**
      * @see java.util.List.<caret>
      */
      class Goo { void goo(Foo foo, Bar bar) {} }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", text);
      assertEquals(0, myFixture.completeBasic().length);
      myFixture.checkResult(text);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testShortNameInJavadocIfWasImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      import java.util.Map;

      /**
       * {@link Ma<caret>}
       */
      class Test {
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFqnInJavadocIfWasNotImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      import java.util.Map;

      /**
       * {@link HashMa<caret>}
       */
      class Test {
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFqnNameInJavadocIfWasImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
    String text = """
      import java.util.Map;
      
      /**
       * {@link Ma<caret>}
       */
      class Test {
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testShortNameInJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    String text = """
      import java.util.Map;

      /**
       * {@link Ma<caret>}
       */
      class Test {
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testShortNameInJavadocIfWasImportOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      import java.util.*;
      
      /**
       * {@link ArraLi<caret>}
       */
      class Test {
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testNullQualifiedName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
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
    EdtTestUtil.runInEdtAndWait(() -> {
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

    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testShortNameIfImplicitlyImported() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    String text = """
      /**
       * {@link Str<caret>}
       */
      class Test {
      }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testShortNameIfInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
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
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testCustomReferenceProvider() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
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
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY, myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("ReferenceProvider.java");
      completion.get().assertFirstStringItems("1", "2", "3");
    });
  }

  @Test
  public void test_complete_author_name() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String userName = SystemProperties.getUserName();
    assertNotNull(userName);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** @author <caret> */");
      myFixture.completeBasic();
      myFixture.type("\n");
      myFixture.checkResult("/** @author " + userName + "<caret> */");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_insert_link_to_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** FileNotFoEx<caret> */");
      myFixture.completeBasic();
      myFixture.checkResult("/** {@link java.io.FileNotFoundException<caret>} */");
    });
  }

  @Test
  @NeedsIndex.Full
  public void test_insert_link_to_inner_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package zoo; public class Outer { public static class FooBarGoo{}}");
      myFixture.configureByText("a.java", "/** FooBarGo<caret> */");
      myFixture.completeBasic();
      myFixture.checkResult("/** {@link zoo.Outer.FooBarGoo<caret>} */");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_insert_link_to_imported_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.io.*; /** FileNotFoEx<caret> */ class A{}");
      myFixture.completeBasic();
      myFixture.checkResult("import java.io.*; /** {@link FileNotFoundException<caret>} */ class A{}");
    });
  }

  @Test
  public void test_insert_link_to_method() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** a. #fo<caret> */ interface Foo { void foo(int a); }}");
      myFixture.completeBasic();
      myFixture.type("\n");

      myFixture.checkResult("/** a. {@link #foo<selection>(int)<caret></selection>} */ interface Foo { void foo(int a); }}");
      assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));

      myFixture.type("\t");
      myFixture.checkResult("/** a. {@link #foo(int)}<caret> */ interface Foo { void foo(int a); }}");
      assertNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_insert_link_to_method_in_a_q_named_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** a. java.io.File#liFi<caret> */ interface Foo {}");
      myFixture.completeBasic();
      myFixture.type("\n");
      myFixture.checkResult("import java.io.File;\n\n/** a. {@link File#listFiles()} */ interface Foo {}");
    });
  }

  @Test
  public void test_insert_link_to_field() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** a. #fo<caret> */ interface Foo { int foo; }}");
      myFixture.completeBasic();
      myFixture.checkResult("/** a. {@link #foo}<caret> */ interface Foo { int foo; }}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_insert_markdown_link_to_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/// FileNotFoEx<caret>\ninterface Foo {}");
      myFixture.completeBasic();
      myFixture.checkResult("/// [java.io.FileNotFoundException<caret>]\ninterface Foo {}");
    });
  }

  @Test
  @NeedsIndex.Full
  public void test_insert_markdown_link_to_inner_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package zoo; public class Outer { public static class FooBarGoo{}}");
      myFixture.configureByText("a.java", "/// FooBarGo<caret>\ninterface Foo {}");
      myFixture.completeBasic();
      myFixture.checkResult("/// [zoo.Outer.FooBarGoo<caret>]\ninterface Foo {}");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_insert_markdown_link_to_imported_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.io.*;\n\n/// FileNotFoEx<caret>\ninterface Foo {}");
      myFixture.completeBasic();
      myFixture.checkResult("import java.io.*;\n\n/// [FileNotFoundException<caret>]\ninterface Foo {}");
    });
  }

  @Test
  public void test_insert_markdown_link_to_method() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/// a. #fo<caret>\ninterface Foo { void foo(int a); }");
      myFixture.completeBasic();
      myFixture.type("\n");

      myFixture.checkResult("/// a. [#foo<selection>(int)<caret></selection>]\ninterface Foo { void foo(int a); }");
      assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));

      myFixture.type("\t");
      myFixture.checkResult("/// a. [#foo(int)]<caret>\ninterface Foo { void foo(int a); }");
      assertNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    });
  }

  /// Square brackets are meaningful in Markdown reference links, so they have to be escaped, see JEP 467. 
  @Test
  public void test_insert_markdown_link_to_method_with_array_parameter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/// a. #fo<caret>\ninterface Foo { void foo(char[] a); }");
      myFixture.completeBasic();
      myFixture.type("\n");
      myFixture.type("\t");
      myFixture.checkResult("/// a. [#foo(char\\[\\])]<caret>\ninterface Foo { void foo(char[] a); }");
    });
  }

  @Test
  public void test_insert_markdown_link_to_field() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/// a. #fo<caret>\ninterface Foo { int foo; }");
      myFixture.completeBasic();
      myFixture.checkResult("/// a. [#foo]<caret>\ninterface Foo { int foo; }");
    });
  }

  @Test
  public void test_wrap_null_into_code_tag() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** nul<caret> */");
      myFixture.completeBasic();
      myFixture.checkResult("/** {@code null}<caret> */");
    });
  }

  @Test
  public void test_null_inside_code_tag() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** {@code nul<caret>} */");
      myFixture.completeBasic();
      myFixture.checkResult("/** {@code null<caret>} */");
    });
  }

  @Test
  public void test_no_link_inside_code_tag() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** {@code FBG<caret>} */ interface FooBarGoo {}");
      myFixture.completeBasic();
      myFixture.checkResult("/** {@code FooBarGoo<caret>} */ interface FooBarGoo {}");
    });
  }

  @Test
  public void test_completing_inside_qualified_name() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** @see java.io.<caret> */");
      myFixture.completeBasic();
      myFixture.getLookup()
        .setCurrentItem(ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("IOException")));
      myFixture.type("\n");
      myFixture.checkResult("/** @see java.io.IOException<caret> */");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_no_hierarchical_generic_method_duplicates() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void test_allow_to_easily_omit_method_parameters() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/** {@link #fo<caret>} */ interface Foo { void foo(int a); }}");
      myFixture.completeBasic();

      myFixture.checkResult("/** {@link #foo<selection>(int)<caret></selection>} */ interface Foo { void foo(int a); }}");
      assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));

      myFixture.type("\b\n");
      myFixture.checkResult("/** {@link #foo}<caret> */ interface Foo { void foo(int a); }}");
      assertNull(TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    });
  }

  @Test
  public void test_tags_at_top_level() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "interface Foo { /**\n * <caret> */void foo(int a); }");
      myFixture.completeBasic();
      assertEquals(Arrays.asList("@apiNote", "@deprecated", "@exception", "@hidden", "@implNote", "@implSpec", "@param", "@param a", "@see",
                                 "@serialData", "@since", "@throws"),
                   myFixture.getLookupElementStrings());
      LookupElement element = myFixture.getLookupElements()[6];
      assertEquals("@param", element.getLookupString());
      completion.get().selectItem(element);
      myFixture.checkResult("interface Foo { /**\n" +
                            " * @param  */void foo(int a); }");
    });
  }

  @Test
  public void test_tags_at_top_level_inline() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "interface Foo { /** Hello <caret> */void foo(int a); }");
      myFixture.completeBasic();
      assertEquals(Arrays.asList("{@code}", "{@docRoot}", "{@index}", "{@inheritDoc}", "{@linkplain}", "{@link}", "{@literal}", "{@snippet}", "{@summary}", "{@systemProperty}", "{@value}"),
                   myFixture.getLookupElementStrings());
      LookupElement element = myFixture.getLookupElements()[5];
      assertEquals("{@link}", element.getLookupString());
      completion.get().selectItem(element);
      myFixture.checkResult("interface Foo { /** Hello {@link <caret>} */void foo(int a); }");
    });
  }

  @Test
  public void test_tags_after_return() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "interface Foo { /** @return <caret> */int foo(int a); }");
      myFixture.completeBasic();
      assertEquals(Arrays.asList("{@code}", "{@docRoot}", "{@index}", "{@inheritDoc}", "{@linkplain}", "{@link}", "{@literal}", "{@return}",
                                 "{@snippet}", "{@summary}", "{@systemProperty}", "{@value}"), myFixture.getLookupElementStrings());
      LookupElement element = myFixture.getLookupElements()[5];
      assert element.getLookupString().equals("{@link}");
      completion.get().selectItem(element);
      myFixture.checkResult("interface Foo { /** @return {@link <caret>} */int foo(int a); }");
    });
  }

  @Test
  public void test_tags_at_top_level_inline_in_brace() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "interface Foo { /** Hello {<caret>} */void foo(int a); }");
      myFixture.completeBasic();
      assertEquals(Arrays.asList("@code", "@docRoot", "@index", "@inheritDoc", "@link", "@linkplain", "@literal", "@snippet", "@summary",
                       "@systemProperty", "@value"), myFixture.getLookupElementStrings());
      LookupElement element = myFixture.getLookupElements()[4];
      assert element.getLookupString().equals("@link");
      completion.get().selectItem(element);
      myFixture.checkResult("interface Foo { /** Hello {@link <caret>} */void foo(int a); }");
    });
  }

  @Test
  public void test_autoinsert_link_with_space() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        myFixture.configureByText("a.java", "/** {@li<caret>} */public class JavadocLink {}");
        myFixture.completeBasic();
        myFixture.type(" ");
        myFixture.checkResult("/** {@link <caret>} */public class JavadocLink {}");
      }
      finally {
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
      }
    });
  }

  @Test
  public void test_custom_tag() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    var inspection = new JavadocDeclarationInspection();
    inspection.registerAdditionalTag("foobar");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.enableInspections(inspection);
      myFixture.configureByText("a.java", "/**\n * @fo<caret>\n */\npublic class Demo {}");
      myFixture.completeBasic();
      myFixture.checkResult("/**\n * @foobar \n */\npublic class Demo {}");
    });
  }

  @Test
  public void test_in_snippet_file() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void test_in_snippet_class() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testInSnippetAttribute() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/**\n * {@snippet cl<caret>}\n */\npublic class Demo {}");
      myFixture.complete(CompletionType.BASIC, 0);
      myFixture.checkResult("/**\n * {@snippet class=<caret>}\n */\npublic class Demo {}");
    });
  }

  @Test
  public void testInSnippetAttribute2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/**\n * {@snippet cl<caret>=x}\n */\npublic class Demo {}");
      myFixture.complete(CompletionType.BASIC, 0);
      myFixture.checkResult("/**\n * {@snippet class=<caret>x}\n */\npublic class Demo {}");
    });
  }

  @Test
  public void testInSnippetAttribute3() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/**\n * {@snippet class=X cl<caret>}\n */\npublic class Demo {}");
      myFixture.complete(CompletionType.BASIC, 0);
      myFixture.checkResult("/**\n * {@snippet class=X cl<caret>}\n */\npublic class Demo {}");
    });
  }

  @Test
  public void testRegionCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testRegionCompletionInQuotes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testLangCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "/**\n * {@snippet lang=j<caret>}\n */\npublic class X {}\n");
      myFixture.complete(CompletionType.BASIC, 0);
      List<String> strings = myFixture.getLookupElementStrings();
      assertTrue(strings.containsAll(Arrays.asList("java", "JShellLanguage", "JVM")));
    });
  }

  @Test
  public void testInlineTagCompletionInTag() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
      /**
       * @see Foo <caret>
       */
       public class Foo {}
      """);
      myFixture.complete(CompletionType.BASIC, 0);
      List<String> strings = myFixture.getLookupElementStrings();
      assertThat(strings).isNotEmpty();
      assertTrue(ContainerUtil.and(strings, s -> s.startsWith("{@")));
    });
  }

  @Test
  public void testTagCompletionAfterTag() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
      /**
       * @see Foo label
       * <caret>
       */
       public class Foo {}
      """);
      myFixture.complete(CompletionType.BASIC, 0);
      List<String> strings = myFixture.getLookupElementStrings();
      assertThat(strings).isNotEmpty();
      assertTrue(ContainerUtil.and(strings, s -> s.startsWith("@")));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testCompletionInPackageInfoDoc() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
      EdtTestUtil.runInEdtAndWait(() -> {
        completion.get().configureByFile("package1/package-info.java");
        myFixture.completeBasic();
        completion.get().checkResultByFile("package1/package-info.after.java");
      });
    }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCompletionInPackageInfoDoc2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
      EdtTestUtil.runInEdtAndWait(() -> {
        completion.get().configureByFile("package2/package-info.java");
        myFixture.type("\n");
        completion.get().checkResultByFile("package2/package-info.after.java");
      });
    }
}

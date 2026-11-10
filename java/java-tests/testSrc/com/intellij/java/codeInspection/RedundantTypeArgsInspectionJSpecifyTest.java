// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.java.codeInsight.JSpecifyTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionTestCase.setupTypeUseAnnotations;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class RedundantTypeArgsInspectionJSpecifyTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private void doTest(@Language("JAVA") String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JSpecifyTestUtil.addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    myFixture.enableInspections(new RedundantTypeArgsInspection());
    myFixture.configureByText("Test.java", text);
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testExplicitTypeArgumentChangesNullability() {
    doTest("""
             import org.jspecify.annotations.NullMarked;
             import org.jspecify.annotations.Nullable;

             @NullMarked
             class Test {
               static <T extends @Nullable Object> T convert(T value) {
                 return value;
               }

               static @Nullable Object nullable() {
                 return null;
               }

               void test() {
                 // the inferred type argument is @Nullable Object, so <Object> is not redundant
                 Object o = Test.<Object>convert(nullable());
                 System.out.println(o);
               }
             }""");
  }

  @Test
  public void testExplicitTypeArgumentKeepsNullability() {
    doTest("""
             import org.jspecify.annotations.NullMarked;
             import org.jspecify.annotations.Nullable;

             @NullMarked
             class Test {
               static <T extends @Nullable Object> T convert(T value) {
                 return value;
               }

               static Object notNull() {
                 return "";
               }

               void test() {
                 Object o = Test.<warning descr="Explicit type arguments can be inferred"><Object></warning>convert(notNull());
                 System.out.println(o);
               }
             }""");
  }

  @Test
  public void testTypeArgumentInferredFromLocalVariable() {
    doTest("""
             import org.jspecify.annotations.NullMarked;

             import java.util.Objects;

             @NullMarked
             class Test {
               void test() {
                 String s = "x";
                 Object o = Objects.<warning descr="Explicit type arguments can be inferred"><String></warning>requireNonNull(s);
                 System.out.println(o);
               }
             }""");
  }

  @Test
  public void testAnnotatedTypeArgumentIsNeverReported() {
    doTest("""
             import org.jspecify.annotations.NullMarked;
             import org.jspecify.annotations.Nullable;

             @NullMarked
             class Test {
               static <T extends @Nullable Object> T convert(T value) {
                 return value;
               }

               static @Nullable Object nullable() {
                 return null;
               }

               void test() {
                 Object o = Test.<@Nullable Object>convert(nullable());
                 System.out.println(o);
               }
             }""");
  }

  @Test
  public void testNullabilityChangeInDeeplyNestedTypeArgument() {
    doTest(nestedListTest(20));
  }

  private static String nestedListTest(int depth) {
    String open = "List<".repeat(depth);
    String close = ">".repeat(depth);
    String typeArgument = "<" + open + "Object" + close + ">";
    return """
      import org.jspecify.annotations.NullMarked;
      import org.jspecify.annotations.Nullable;

      import java.util.List;

      @NullMarked
      class Test {
        static <T> T id(T value) {
          return value;
        }

        static %1$s@Nullable Object%2$s nested() {
          throw new UnsupportedOperationException();
        }

        void test() {
          Object o = Test.%3$sid(nested());
          System.out.println(o);
        }
      }""".formatted(open, close, typeArgument);
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.java.codeInsight.JSpecifyTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.intellij.java.codeInspection.DataFlowInspectionTestCase.setupTypeUseAnnotations;

public class RedundantTypeArgsInspectionJSpecifyTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  private void doTest(@Language("JAVA") String text) {
    JSpecifyTestUtil.addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    myFixture.enableInspections(new RedundantTypeArgsInspection());
    myFixture.configureByText("Test.java", text);
    myFixture.testHighlighting(true, false, false);
  }

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
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.java18api.OptionalGetWithoutIsPresentInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class OptionalGetWithoutIsPresentInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/optionalGet");
  }

  @Test
  public void testAbstractEnum() { doTest(); }
  @Test
  public void testFinalInheritance() { doTest(); }
  @Test
  public void testOptionalGet() { doTest(); }
  @Test
  public void testOptionalGetInlineLambda() { doTest(); }
  @Test
  public void testOptionalGetMethodReference() { doTest(); }
  @Test
  public void testStreamGroupingBy() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    mockClasses();
    myFixture.enableInspections(new OptionalGetWithoutIsPresentInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  private void mockClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    myFixture.addClass("package org.testng;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    myFixture.addClass("""
                         package com.google.common.base;

                         public interface Supplier<T> { T get();}
                         """);
    myFixture.addClass("""
                         package com.google.common.base;

                         public interface Function<F, T> { T apply(F input);}
                         """);
    myFixture.addClass("""
                         package com.google.common.base;

                         public abstract class Optional<T> {
                           public static <T> Optional<T> absent() {}
                           public static <T> Optional<T> of(T ref) {}
                           public static <T> Optional<T> fromNullable(T ref) {}
                           public abstract T get();
                           public abstract boolean isPresent();
                           public abstract T orNull();
                           public abstract T or(Supplier<? extends T> supplier);
                           public abstract <V> Optional<V> transform(Function<? super T, V> fn);
                           public abstract T or(T val);
                           public abstract java.util.Optional<T> toJavaUtil();
                         }""");

  }
}
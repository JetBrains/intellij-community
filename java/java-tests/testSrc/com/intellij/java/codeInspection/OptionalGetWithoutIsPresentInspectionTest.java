// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.java18api.OptionalGetWithoutIsPresentInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class OptionalGetWithoutIsPresentInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/optionalGet";
  }

  public void testAbstractEnum() { doTest(); }
  public void testFinalInheritance() { doTest(); }
  public void testOptionalGet() { doTest(); }
  public void testOptionalGetInlineLambda() { doTest(); }
  public void testOptionalGetMethodReference() { doTest(); }
  public void testStreamGroupingBy() { doTest(); }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  private void doTest() {
    mockClasses();
    myFixture.enableInspections(new OptionalGetWithoutIsPresentInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  private void mockClasses() {
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
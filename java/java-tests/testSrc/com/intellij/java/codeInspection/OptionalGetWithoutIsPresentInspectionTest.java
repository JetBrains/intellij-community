// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.java18api.OptionalGetWithoutIsPresentInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class OptionalGetWithoutIsPresentInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/optionalGet";
  }

  public void testOptionalGet() { doTest(); }

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
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public interface Supplier<T> { T get();}\n");
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public interface Function<F, T> { T apply(F input);}\n");
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public abstract class Optional<T> {\n" +
                       "  public static <T> Optional<T> absent() {}\n" +
                       "  public static <T> Optional<T> of(T ref) {}\n" +
                       "  public static <T> Optional<T> fromNullable(T ref) {}\n" +
                       "  public abstract T get();\n" +
                       "  public abstract boolean isPresent();\n" +
                       "  public abstract T orNull();\n" +
                       "  public abstract T or(Supplier<? extends T> supplier);\n" +
                       "  public abstract <V> Optional<V> transform(Function<? super T, V> fn);\n" +
                       "  public abstract T or(T val);\n" +
                       "  public abstract java.util.Optional<T> toJavaUtil();\n" +
                       "}");

  }
}
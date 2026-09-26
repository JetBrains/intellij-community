// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.IncorrectLazyConstantUsageInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.style.FieldMayBeFinalInspection;
import org.jetbrains.annotations.NotNull;

public class IncorrectLazyCollectionUsageInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testLazyCollectionUsage() {
    doTest();
  }

  public void testNoDuplicateWithFieldMayBeFinalLazyCollection() {
    myFixture.enableInspections(new FieldMayBeFinalInspection());
    doTest();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new IncorrectLazyConstantUsageInspection());
    myFixture.addClass("""
                         package java.util;
                         import java.util.function.IntFunction;
                         public interface List<E> extends Collection<E> {
                             static <E> List<E> ofLazy(int size, IntFunction<? extends E> fn) { return null; }
                             static <E> List<E> of(E... elements) { return null; }
                         }
                         """);
    myFixture.addClass("package java.util; public interface Collection<E> {}");
    myFixture.addClass("""
                         package java.util;
                         import java.util.function.Function;
                         public interface Map<K, V> {
                             static <K, V> Map<K, V> ofLazy(int size, Function<Integer, Map.Entry<K, V>> fn) { return null; }
                             interface Entry<K, V> {}
                             static Entry<K, V> entry(K key, V value) { return null; }
                         }
                         """);
    myFixture.addClass("package java.util.function; public interface IntFunction<T> { T apply(int i);}");
    myFixture.addClass("package java.util.function; public interface Function<T, R> { R apply(T t);}");
    myFixture.addClass("package java.lang; public class String {}");
    myFixture.addClass("package java.lang; public class Integer {}");
  }

  private void doTest() {
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new ProjectDescriptor(LanguageLevel.JDK_26_PREVIEW) {
      //otherwise it is impossible to override List, Map
      @Override
      public Sdk getSdk() {
        return null;
      }
    };
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/incorrectLazyConstantUsage";
  }
}

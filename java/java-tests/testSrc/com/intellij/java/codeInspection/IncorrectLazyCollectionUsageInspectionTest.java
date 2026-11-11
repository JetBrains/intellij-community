// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.IncorrectLazyConstantUsageInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.style.FieldMayBeFinalInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class IncorrectLazyCollectionUsageInspectionTest {
  private static final LightJavaCodeInsightFixtureTestCase.ProjectDescriptor DESCRIPTOR =
    new LightJavaCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.JDK_26_PREVIEW) {
      //otherwise it is impossible to override List, Map
      @Override
      public Sdk getSdk() {
        return null;
      }
    };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testLazyCollectionUsage() {
    doTest();
  }

  @Test
  public void testNoDuplicateWithFieldMayBeFinalLazyCollection() {
    codeInsightFixture.get().enableInspections(new FieldMayBeFinalInspection());
    doTest();
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/incorrectLazyConstantUsage");
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".java");
  }
}

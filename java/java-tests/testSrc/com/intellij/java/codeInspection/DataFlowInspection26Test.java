// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowInspection26Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_26);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  @SuppressWarnings("Since15")
  public void testLazyConstantBasics() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package java.lang;
                         import java.util.function.Supplier;
                         
                         public final class LazyConstant<V> {
                             public static native <V> LazyConstant<V> of(Supplier<? extends V> supplier);
                         
                             public V get();
                         }
                         """);
    dataFlow.get().doTestWith((dfi, _) -> dfi.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = false);
  }

  @Test
  public void testMatchExceptionWithPrimitivePatterns() { dataFlow.get().doTest(); }

  @Test
  public void testTrackRecordFields() {
    dataFlow.get().doTest();
  }

  @Test
  public void testMapGetOrDefaultNullability() { dataFlow.get().doTest(); }
}
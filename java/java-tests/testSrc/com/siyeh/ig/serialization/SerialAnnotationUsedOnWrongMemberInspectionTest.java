// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class SerialAnnotationUsedOnWrongMemberInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_15);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SerialAnnotationUsedOnWrongMemberInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[]{"""
                          package java.io;
                          import java.lang.annotation.*;
                          @Target({ElementType.METHOD, ElementType.FIELD})
                          @Retention(RetentionPolicy.SOURCE)
                          public @interface Serial  {}
                          """
    };
  }

  @Test
  public void testSerializableClassPositive() { inspectionFixture.get().doTest(); }
  @Test
  public void testSerializableClassNegative() { inspectionFixture.get().doTest(); }
  @Test
  public void testExternalizableClassPositive() { inspectionFixture.get().doTest(); }
  @Test
  public void testExternalizableClassNegative() { inspectionFixture.get().doTest(); }
  @Test
  public void testSuppressedSerial() { inspectionFixture.get().doTest(); }
}

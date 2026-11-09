// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class AssignmentOrReturnOfFieldWithMutableTypeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new AssignmentOrReturnOfFieldWithMutableTypeInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
      package com.google.common.collect;
      
      import java.util.List;
      
      public class ImmutableList<E> implements List<E> {
        public static ImmutableList<?> of() {return new ImmutableList<>();}
        public static <T> ImmutableList<T> copyOf(List<T> list) {return new ImmutableList<>();}
      }"""
    };
  }

  @Test
  public void testAssignmentOrReturnOfFieldWithMutableType() {
    inspectionFixture.get().doTest();
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class AssignmentOrReturnOfFieldWithMutableTypeInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new AssignmentOrReturnOfFieldWithMutableTypeInspection(), getEnvironmentClasses());

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
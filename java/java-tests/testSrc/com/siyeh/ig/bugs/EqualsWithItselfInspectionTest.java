// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class EqualsWithItselfInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

  private EqualsWithItselfInspection myInspection;

  @Test
  public void testEqualsWithItself() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testEqualsWithItself_ignoreNonFinalClassesInTest() {
    myInspection.ignoreNonFinalClassesInTest = true;
    inspectionFixture.get().doTest();
  }

  private @NotNull InspectionProfileEntry getInspection() {
    myInspection = new EqualsWithItselfInspection();
    return myInspection;
  }

  @SuppressWarnings({"unchecked", "rawtypes", "NonFinalUtilityClass"})
  private static String[] getEnvironmentClasses() {
    return new String[]{"""
     package org.junit.jupiter.api;
     public class Assertions{
     	public static void assertEquals(Object expected, Object actual) {}
     	public static void assertNotEquals(Object expected, Object actual) {}
     	public static void assertArrayEquals(int[] expected, int[] actual) {}
     }
     """,
      """
     package org.junit;
     public class Assert{
     	public static void assertSame(Object expected, Object actual) {}
     }
     """,
      """
     package org.assertj.core.api;
     import org.assertj.core.api.AbstractAssert;
     public class Assertions{
      public static AbstractAssert assertThat(Object actual) {
        return new AbstractAssert();
      }
     }
     """,
      """
     package org.assertj.core.api;
     public class AbstractAssert{
      public AbstractAssert isEqualTo(Object expected) {
       return this;
      }
      public AbstractAssert anotherTest(Object expected) {
       return this;
      }
     }
     """,
      """
     package org.testng;
     public class Assert{
     	public static void assertEquals(Object expected, Object actual) {}
     }
     """,
    };
  }
}

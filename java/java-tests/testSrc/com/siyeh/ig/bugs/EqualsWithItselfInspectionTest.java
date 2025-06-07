// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EqualsWithItselfInspectionTest extends LightJavaInspectionTestCase {

  private EqualsWithItselfInspection myInspection;

  public void testEqualsWithItself() {
    doTest();
  }

  public void testEqualsWithItself_ignoreNonFinalClassesInTest() {
    myInspection.ignoreNonFinalClassesInTest = true;
    doTest();
  }

  @Override
  protected @NotNull InspectionProfileEntry getInspection() {
    myInspection = new EqualsWithItselfInspection();
    return myInspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  @SuppressWarnings({"unchecked", "rawtypes", "NonFinalUtilityClass"})
  @Override
  protected String[] getEnvironmentClasses() {
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

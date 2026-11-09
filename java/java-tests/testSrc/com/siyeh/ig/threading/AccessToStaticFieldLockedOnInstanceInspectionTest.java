// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class AccessToStaticFieldLockedOnInstanceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses(),
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/threading/access_to_static_field_locked_on_instance_data");

  @Test
  public void testAccessToStaticFieldLockedOnInstanceData() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final AccessToStaticFieldLockedOnInstanceInspection inspection = new AccessToStaticFieldLockedOnInstanceInspection();
    inspection.ignoredClasses.add("java.util.List");
    return inspection;
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package javax.annotation.concurrent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ThreadSafe {
}"""
    };
  }
}
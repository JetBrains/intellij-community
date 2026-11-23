// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dependency;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.siyeh.ig.dependency.SuspiciousPackagePrivateAccessFixtureKt.suspiciousPackagePrivateAccessFixture;
import static com.siyeh.ig.dependency.SuspiciousPackagePrivateAccessFixtureKt.suspiciousPackagePrivateAccessProjectFixture;

@TestApplication
public class SuspiciousPackagePrivateAccessInspectionTest {
  private static final TestFixture<SuspiciousPackagePrivateAccessProject> projectFixture = suspiciousPackagePrivateAccessProjectFixture();
  private final TestFixture<SuspiciousPackagePrivateAccessFixture> inspectionFixture = suspiciousPackagePrivateAccessFixture(
    projectFixture, "java",
    LightJavaInspectionFixture.inspectionTestDataPath(LightJavaInspectionFixture.inspectionBasePath(SuspiciousPackagePrivateAccessInspection.class)));

  @BeforeEach
  void setUpDepTests() {
    inspectionFixture.get().getFixture().copyDirectoryToProject("depTests", "../depTests");
  }

  @Test
  public void testAccessingPackagePrivateMembers() {
    inspectionFixture.get().doTestWithDependency();
  }

  @Test
  public void testAccessingProtectedMembers() {
    inspectionFixture.get().doTestWithDependency();
  }

  @Test
  public void testAccessingProtectedMembersFromDifferentPackage() {
    inspectionFixture.get().doTestWithDependency();
  }

  @Test
  public void testAccessingPackagePrivateInSignatures() {
    inspectionFixture.get().doTestWithDependency();
  }

  @Test
  public void testOverridePackagePrivateMethod() {
    inspectionFixture.get().doTestWithDependency();
  }

  @Test
  public void testPackagePrivateClassTest() {
    SuspiciousPackagePrivateAccessFixture fixture = inspectionFixture.get();
    fixture.getFixture().configureByFile("../depTests/xxx/PackagePrivateClassTest.java");
    fixture.getFixture().testHighlighting(true, false, false);
  }
}

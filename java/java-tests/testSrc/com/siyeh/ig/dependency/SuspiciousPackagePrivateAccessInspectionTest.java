// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dependency;

public class SuspiciousPackagePrivateAccessInspectionTest extends SuspiciousPackagePrivateAccessInspectionTestCase {
  public SuspiciousPackagePrivateAccessInspectionTest() {super("java");}

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject("depTests", "../depTests");
  }

  public void testAccessingPackagePrivateMembers() {
    doTestWithDependency();
  }

  public void testAccessingProtectedMembers() {
    doTestWithDependency();
  }

  public void testAccessingProtectedMembersFromDifferentPackage() {
    doTestWithDependency();
  }

  public void testAccessingPackagePrivateInSignatures() {
    doTestWithDependency();
  }

  public void testOverridePackagePrivateMethod() {
    doTestWithDependency();
  }
  
  public void testPackagePrivateClassTest() {
    myFixture.configureByFile("../depTests/xxx/PackagePrivateClassTest.java");
    myFixture.testHighlighting(true, false, false);
  }
}

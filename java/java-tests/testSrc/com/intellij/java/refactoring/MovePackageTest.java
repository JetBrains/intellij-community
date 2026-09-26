// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;

public class MovePackageTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/movePackage/";
  }

  public void testMoveSingle() {
    doTest(new String[]{"pack1"}, "target");
  }

  public void testQualifiedRef() {
    doTest(new String[]{"package1.test"}, "package2");
  }

  public void testInsidePackage() {
    doTest(new String[]{"a"}, "a.b");
  }

  public void testPackageAndReferencedClass() {
    doTest(() -> performAction(new PsiElement[]{
      myFixture.findPackage("a"),
      myFixture.findClass("B")}, "b"));
  }

  private void doTest(final String[] packageNames, final String newPackageName) {
    doTest(() -> this.performAction(packageNames, newPackageName));
  }

  private void performAction(String[] packageNames, String newPackageName) {
    final PsiPackage[] packages = new PsiPackage[packageNames.length];
    for (int i = 0; i < packages.length; i++) {
      String packageName = packageNames[i];
      packages[i] = myFixture.findPackage(packageName);
      assertNotNull("Package " + packageName + " not found", packages[i]);
    }

    performAction(packages, newPackageName);
  }

  private void performAction(PsiElement[] packagesAndClasses, String newPackageName) {
    PsiPackage newParentPackage = myFixture.findPackage(newPackageName);
    assertNotNull(newParentPackage);
    final PsiDirectory[] dirs = newParentPackage.getDirectories();
    assertEquals(1, dirs.length);

    new MoveClassesOrPackagesProcessor(getProject(), packagesAndClasses,
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(newParentPackage), dirs[0]),
                                       true, false, null).run();
  }
}

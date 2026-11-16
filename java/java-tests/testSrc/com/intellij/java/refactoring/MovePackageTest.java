// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MovePackageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/movePackage/");

  @Test
  public void testMoveSingle() {
    doTest(new String[]{"pack1"}, "target");
  }

  @Test
  public void testQualifiedRef() {
    doTest(new String[]{"package1.test"}, "package2");
  }

  @Test
  public void testInsidePackage() {
    doTest(new String[]{"a"}, "a.b");
  }

  @Test
  public void testPackageAndReferencedClass() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> performAction(new PsiElement[]{
      myFixture.findPackage("a"),
      myFixture.findClass("B")}, "b"));
  }

  private void doTest(final String[] packageNames, final String newPackageName) {
    multiFile.get().doTest(() -> this.performAction(packageNames, newPackageName));
  }

  private void performAction(String[] packageNames, String newPackageName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final PsiPackage[] packages = new PsiPackage[packageNames.length];
    for (int i = 0; i < packages.length; i++) {
      String packageName = packageNames[i];
      packages[i] = myFixture.findPackage(packageName);
      assertNotNull(packages[i], "Package " + packageName + " not found");
    }

    performAction(packages, newPackageName);
  }

  private void performAction(PsiElement[] packagesAndClasses, String newPackageName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    PsiPackage newParentPackage = myFixture.findPackage(newPackageName);
    assertNotNull(newParentPackage);
    final PsiDirectory[] dirs = newParentPackage.getDirectories();
    assertEquals(1, dirs.length);

    new MoveClassesOrPackagesProcessor(myFixture.getProject(), packagesAndClasses,
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(newParentPackage), dirs[0]),
                                       true, false, null).run();
  }
}

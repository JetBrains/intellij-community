/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import org.jetbrains.annotations.NotNull;

public class MovePackageTest extends MultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testMoveSingle() {
    doTest(new String[]{"pack1"}, "target");
  }

/* IMPLEMENT: soft references in JSP
  public void testJsp() throws Exception {
    doTest(new String[]{"pack1"}, "target");
  }
*/
  public void testQualifiedRef() {
    doTest(new String[]{"package1.test"}, "package2");
  }

  public void testInsidePackage() {
    doTest(new String[]{"a"}, "a.b");
  }

  public void testPackageAndReferencedClass() {
    Project project = myPsiManager.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    doTest((rootDir, rootAfter) -> performAction(new PsiElement[]{facade.findPackage("a"), facade.findClass("B", GlobalSearchScope.allScope(project))}, "b"));
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/movePackage/";
  }

  private void doTest(final String[] packageNames, final String newPackageName) {
    doTest((rootDir, rootAfter) -> this.performAction(packageNames, newPackageName));
  }

  private void performAction(String[] packageNames, String newPackageName) {
    final PsiPackage[] packages = new PsiPackage[packageNames.length];
    for (int i = 0; i < packages.length; i++) {
      String packageName = packageNames[i];
      packages[i] = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(packageName);
      assertNotNull("Package " + packageName + " not found", packages[i]);
    }

    performAction(packages, newPackageName);
  }

  private void performAction(PsiElement[] packagesAndClasses, String newPackageName) {
    PsiPackage newParentPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(newPackageName);
    assertNotNull(newParentPackage);
    final PsiDirectory[] dirs = newParentPackage.getDirectories();
    assertEquals(dirs.length, 1);

    new MoveClassesOrPackagesProcessor(myProject, packagesAndClasses,
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(newParentPackage), dirs[0]),
                                       true, false, null).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}

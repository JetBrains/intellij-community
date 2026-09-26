// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.idea.TestFor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

@TestFor(issues = "IDEA-393763")
public class DeclaredPackageResolveTest extends LightJavaCodeInsightFixtureTestCase {

  public void testWildcardImportOfDeclaredPackage() {
    addClassA();
    myFixture.configureByText("ClassTest.java", """
      package examples;
      
      import org.classa.*;
      
      class ClassTest {
        ClassA simple = new ClassA();
        org.classa.ClassA qualified = new org.classa.ClassA();
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testSingleTypeImportOfDeclaredPackage() {
    addClassA();
    myFixture.configureByText("ClassTest.java", """
      package examples;
      
      import org.classa.ClassA;
      
      class ClassTest {
        ClassA simple = new ClassA();
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testFilesWhichDeclareTheSamePackage() {
    myFixture.addFileToProject("Helper.java", """
      package examples;
      
      public class Helper { }
      """);
    myFixture.configureByText("ClassTest.java", """
      package examples;
      
      class ClassTest {
        Helper helper = new Helper();
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testUseScopeOfPackageLocalClassInDeclaredPackage() {
    myFixture.addFileToProject("Helper.java", """
      package examples;

      class Helper { }
      """);
    myFixture.addFileToProject("ClassTest.java", """
      package examples;

      class ClassTest {
        Helper helper = new Helper();
      }
      """);
    PsiClass helper = JavaPsiFacade.getInstance(getProject()).findClass("examples.Helper", GlobalSearchScope.projectScope(getProject()));
    assertNotNull(helper);
    assertEquals(2, ReferencesSearch.search(helper).findAll().size());
  }

  public void testFindDeclaredPackage() {
    addClassA();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());

    PsiPackage declared = facade.findPackage("org.classa");
    assertNotNull(declared);
    PsiClass[] classes = declared.getClasses();
    assertEquals("ClassA", assertOneElement(classes).getName());

    PsiPackage parent = facade.findPackage("org");
    assertNotNull(parent);
    assertTrue(ContainerUtil.exists(parent.getSubPackages(), aPackage -> "org.classa".equals(aPackage.getQualifiedName())));
  }

  public void testPackageScopeOfDeclaredSubpackage() {
    PsiFile nested = myFixture.addFileToProject("Nested.java", """
      package org.classa.sub;

      public class Nested { }
      """);
    PsiFile sibling = myFixture.addFileToProject("Sibling.java", """
      package org.classb;

      public class Sibling { }
      """);
    VirtualFile nestedFile = nested.getVirtualFile();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());

    PsiPackage sub = facade.findPackage("org.classa.sub");
    assertNotNull(sub);
    assertTrue(PackageScope.packageScope(sub, false).contains(nestedFile));

    // no directory holds org.classa, so only the subpackage mode reaches a file which declares a subpackage of it
    PsiPackage classa = facade.findPackage("org.classa");
    assertNotNull(classa);
    assertFalse(PackageScope.packageScope(classa, false).contains(nestedFile));
    assertTrue(PackageScope.packageScope(classa, true).contains(nestedFile));
    assertFalse(PackageScope.packageScope(classa, true).contains(sibling.getVirtualFile()));

    PsiPackage org = facade.findPackage("org");
    assertNotNull(org);
    assertTrue(PackageScope.packageScope(org, true).contains(nestedFile));
    assertTrue(PackageScope.packageScope(org, true).contains(sibling.getVirtualFile()));
  }

  public void testUnknownPackageIsStillUnresolved() {
    addClassA();
    assertNull(JavaPsiFacade.getInstance(getProject()).findPackage("org.classb"));
  }

  private void addClassA() {
    myFixture.addFileToProject("ClassA.java", """
      package org.classa;
      
      public class ClassA { }
      """);
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestFor(issues = "IDEA-393763")
@TestApplication
public class DeclaredPackageResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testWildcardImportOfDeclaredPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testSingleTypeImportOfDeclaredPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testFilesWhichDeclareTheSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testUseScopeOfPackageLocalClassInDeclaredPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> {
      PsiClass helper = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("examples.Helper", GlobalSearchScope.projectScope(myFixture.getProject()));
      assertNotNull(helper);
      assertEquals(2, ReferencesSearch.search(helper).findAll().size());
    });
  }

  @Test
  public void testFindDeclaredPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassA();
    ReadAction.runBlocking(() -> {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());

      PsiPackage declared = facade.findPackage("org.classa");
      assertNotNull(declared);
      PsiClass[] classes = declared.getClasses();
      assertEquals("ClassA", assertThat(classes).singleElement().actual().getName());

      PsiPackage parent = facade.findPackage("org");
      assertNotNull(parent);
      assertTrue(ContainerUtil.exists(parent.getSubPackages(), aPackage -> "org.classa".equals(aPackage.getQualifiedName())));
    });
  }

  @Test
  public void testPackageScopeOfDeclaredSubpackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile nested = myFixture.addFileToProject("Nested.java", """
      package org.classa.sub;

      public class Nested { }
      """);
    PsiFile sibling = myFixture.addFileToProject("Sibling.java", """
      package org.classb;

      public class Sibling { }
      """);
    ReadAction.runBlocking(() -> {
      VirtualFile nestedFile = nested.getVirtualFile();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());

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
    });
  }

  @Test
  public void testUnknownPackageIsStillUnresolved() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addClassA();
    ReadAction.runBlocking(() -> assertNull(JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("org.classb")));
  }

  @Test
  public void testDeclaredPackageAndItsParentsHaveContentInScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addMyService();
    ReadAction.runBlocking(() -> {
      GlobalSearchScope scope = GlobalSearchScope.projectScope(myFixture.getProject());

      assertTrue(findPackageImpl("acme").mayHaveContentInScope(scope));
      assertTrue(findPackageImpl("acme.corp").mayHaveContentInScope(scope));
      assertTrue(findPackageImpl("acme.corp.tools").mayHaveContentInScope(scope));
    });
  }

  @Test
  public void testDeclaredPackageHasNoContentOutsideScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addMyService();
    ReadAction.runBlocking(() -> {
      GlobalSearchScope scope = GlobalSearchScope.notScope(GlobalSearchScope.projectScope(myFixture.getProject()));

      assertFalse(findPackageImpl("acme").mayHaveContentInScope(scope));
      assertFalse(findPackageImpl("acme.corp.tools").mayHaveContentInScope(scope));
    });
  }

  private PsiPackageImpl findPackageImpl(String qualifiedName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> assertInstanceOf(PsiPackageImpl.class, JavaPsiFacade.getInstance(myFixture.getProject()).findPackage(qualifiedName)));
  }

  private void addMyService() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("MyService.java", """
      package acme.corp.tools;

      public class MyService { }
      """);
  }

  private void addClassA() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("ClassA.java", """
      package org.classa;
      
      public class ClassA { }
      """);
  }
}

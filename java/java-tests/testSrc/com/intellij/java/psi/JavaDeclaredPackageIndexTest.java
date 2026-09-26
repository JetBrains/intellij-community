// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.java.JavaDeclaredPackageIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;
import java.util.Set;

public class JavaDeclaredPackageIndexTest extends LightJavaCodeInsightFixtureTestCase {

  public void testNestedPackage() {
    myFixture.addFileToProject("ClassA.java", """
      package org.classa;
      
      public class ClassA { }
      """);

    assertTrue(packageExists(""));
    assertTrue(packageExists("org"));
    assertTrue(packageExists("org.classa"));
    assertFalse(packageExists("org.classb"));
    assertFalse(packageExists("classa"));

    assertTrue(subPackageNames("").contains("org"));
    assertEquals(Set.of("classa"), subPackageNames("org"));
    assertEquals(Set.of(), subPackageNames("org.classa"));

    assertEquals("ClassA.java", assertOneElement(filesWithExactPackage("org.classa")).getName());
    assertEmpty(filesWithExactPackage("org"));

    assertEquals("ClassA.java", assertOneElement(filesWithPackageOrSubPackage("org.classa")).getName());
    assertEquals("ClassA.java", assertOneElement(filesWithPackageOrSubPackage("org")).getName());
    assertEquals("ClassA.java", assertOneElement(filesWithPackageOrSubPackage("")).getName());
    assertEmpty(filesWithPackageOrSubPackage("org.classb"));
  }

  public void testDefaultPackage() {
    myFixture.addFileToProject("ClassA.java", """
      public class ClassA { }
      """);

    assertTrue(packageExists(""));
    assertEquals("ClassA.java", assertOneElement(filesWithExactPackage("")).getName());
  }

  public void testEditOfThePackageStatement() {
    PsiFile psiFile = myFixture.addFileToProject("ClassA.java", """
      package org.classa;

      public class ClassA { }
      """);
    assertTrue(packageExists("org.classa"));
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findPackage("org.classa"));

    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.setText("""
        package org.classb;

        public class ClassA { }
        """);
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    });

    assertFalse(packageExists("org.classa"));
    assertTrue(packageExists("org.classb"));
    assertNull(JavaPsiFacade.getInstance(getProject()).findPackage("org.classa"));
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findPackage("org.classb"));
  }

  private boolean packageExists(String packageName) {
    return JavaDeclaredPackageIndex.packageExists(packageName, scope());
  }

  private Set<String> subPackageNames(String packageName) {
    return JavaDeclaredPackageIndex.getSubPackageNames(packageName, scope());
  }

  private List<VirtualFile> filesWithExactPackage(String packageName) {
    return JavaDeclaredPackageIndex.getFilesWithExactPackage(packageName, scope());
  }

  private List<VirtualFile> filesWithPackageOrSubPackage(String packageName) {
    return JavaDeclaredPackageIndex.getFilesWithPackageOrSubPackage(packageName, scope());
  }

  private GlobalSearchScope scope() {
    return GlobalSearchScope.allScope(getProject());
  }
}

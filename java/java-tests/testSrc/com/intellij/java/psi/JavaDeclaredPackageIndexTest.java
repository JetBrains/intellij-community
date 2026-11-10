// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.java.JavaDeclaredPackageIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaDeclaredPackageIndexTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNestedPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    assertEquals("ClassA.java", assertThat(filesWithExactPackage("org.classa")).singleElement().actual().getName());
    assertThat(filesWithExactPackage("org")).isNullOrEmpty();

    assertEquals("ClassA.java", assertThat(filesWithPackageOrSubPackage("org.classa")).singleElement().actual().getName());
    assertEquals("ClassA.java", assertThat(filesWithPackageOrSubPackage("org")).singleElement().actual().getName());
    assertEquals("ClassA.java", assertThat(filesWithPackageOrSubPackage("")).singleElement().actual().getName());
    assertThat(filesWithPackageOrSubPackage("org.classb")).isNullOrEmpty();
  }

  @Test
  public void testDefaultPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("ClassA.java", """
      public class ClassA { }
      """);

    assertTrue(packageExists(""));
    assertEquals("ClassA.java", assertThat(filesWithExactPackage("")).singleElement().actual().getName());
  }

  @Test
  public void testEditOfThePackageStatement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = myFixture.addFileToProject("ClassA.java", """
      package org.classa;

      public class ClassA { }
      """);
    assertTrue(packageExists("org.classa"));
    ReadAction.runBlocking(() -> assertNotNull(JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("org.classa")));

    Document document = ReadAction.computeBlocking(() -> PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(psiFile));
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      document.setText("""
        package org.classb;

        public class ClassA { }
        """);
      PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(document);
    });

    assertFalse(packageExists("org.classa"));
    assertTrue(packageExists("org.classb"));
    ReadAction.runBlocking(() -> {
      assertNull(JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("org.classa"));
      assertNotNull(JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("org.classb"));
    });
  }

  private boolean packageExists(String packageName) {
    return ReadAction.computeBlocking(() -> JavaDeclaredPackageIndex.packageExists(packageName, scope()));
  }

  private Set<String> subPackageNames(String packageName) {
    return ReadAction.computeBlocking(() -> JavaDeclaredPackageIndex.getSubPackageNames(packageName, scope()));
  }

  private List<VirtualFile> filesWithExactPackage(String packageName) {
    return ReadAction.computeBlocking(() -> JavaDeclaredPackageIndex.getFilesWithExactPackage(packageName, scope()));
  }

  private List<VirtualFile> filesWithPackageOrSubPackage(String packageName) {
    return ReadAction.computeBlocking(() -> JavaDeclaredPackageIndex.getFilesWithPackageOrSubPackage(packageName, scope()));
  }

  private GlobalSearchScope scope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return GlobalSearchScope.allScope(myFixture.getProject());
  }
}

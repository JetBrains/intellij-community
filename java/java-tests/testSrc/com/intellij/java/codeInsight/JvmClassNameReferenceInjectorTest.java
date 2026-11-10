// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil.findReferenceOfClass;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JvmClassNameReferenceInjectorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testClassReference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                           package demo;
                           public class User {}
                         """);

    myFixture.configureByText("Demo.java", """
        public class Demo {
          public void run() {
            String x = /* language=jvm-class-name */ "demo.U<caret>ser";
          }
        }
      """);

    JavaClassReference reference = findReferenceOfClass(myFixture.getReferenceAtCaretPosition(), JavaClassReference.class);
    assertNotNull(reference, "there must be a JavaClassReference");

    assertEquals("User", reference.getCanonicalText());

    PsiElement resolved = ReadAction.computeBlocking(() -> reference.resolve());
    assertTrue(resolved instanceof PsiClass, "references resolves to PsiClass");
  }
  
  @Test
  public void testNestedClassReference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                           package demo;
                           public class User {
                             public static class Nested {}
                           }
                         """);
    myFixture.configureByText("Demo.java", """
        public class Demo {
          public void run() {
            String x = /* language=jvm-class-name */ "demo.User$N<caret>ested";
          }
        }
      """);
    JavaClassReference reference = findReferenceOfClass(myFixture.getReferenceAtCaretPosition(), JavaClassReference.class);
    assertNotNull(reference, "there must be a JavaClassReference");

    assertEquals("Nested", reference.getCanonicalText());

    PsiElement resolved = ReadAction.computeBlocking(() -> reference.resolve());
    assertTrue(resolved instanceof PsiClass, "references resolves to PsiClass");
  }
}

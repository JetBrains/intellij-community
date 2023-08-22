// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import static com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil.findReferenceOfClass;

public class JvmClassNameReferenceInjectorTest extends LightJavaCodeInsightFixtureTestCase {
  public void testClassReference() {
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
    assertNotNull("there must be a JavaClassReference", reference);

    assertEquals("User", reference.getCanonicalText());

    PsiElement resolved = reference.resolve();
    assertTrue("references resolves to PsiClass", resolved instanceof PsiClass);
  }
}

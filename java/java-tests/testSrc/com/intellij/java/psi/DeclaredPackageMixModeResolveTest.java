// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.idea.TestFor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DeclaredPackageMixModeResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_25);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  @TestFor(issues = "IDEA-393763")
  public void testWildcardImportOfDeclaredPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("ClassA.java", """
      package org.classa;

      public class ClassA {
        public void foo() { }
      }
      """);
    myFixture.configureByText("ClassTest.java", """
      package examples;

      import org.classa.*;

      public class ClassTest {
        void test() {
          ClassA a = new ClassA();
          org.classa.ClassA a1 = new org.classa.ClassA();
          a.foo();
          a1.foo();
        }
      }
      """);
    myFixture.checkHighlighting();
  }
}

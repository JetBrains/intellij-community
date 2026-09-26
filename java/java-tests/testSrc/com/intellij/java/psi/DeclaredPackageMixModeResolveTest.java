// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.idea.TestFor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class DeclaredPackageMixModeResolveTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_25;
  }

  @TestFor(issues = "IDEA-393763")
  public void testWildcardImportOfDeclaredPackage() {
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

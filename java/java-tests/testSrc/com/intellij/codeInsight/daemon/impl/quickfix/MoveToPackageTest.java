// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MoveToPackageTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testSimple() {
    PsiClass bClass = myFixture.addClass("package foo;\nimport bar.A;\npublic final class B extends A {}");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B {}");
    invokeFix("Move class 'B' to package 'bar'");
    assertEquals("package bar;\n\npublic final class B extends A {}", bClass.getContainingFile().getText());
  }

  public void testNestedClass() {
    PsiClass bClass = myFixture.addClass("package foo;\nimport bar.A;\npublic class B { public static final class C extends A {} }");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B.C {}");
    invokeFix("Move class 'C' to package 'bar'");
    assertEquals("package bar;\n\npublic class B { public static final class C extends A {} }",
                 bClass.getContainingFile().getText());
  }

  public void testNonAccessibleClass() {
    myFixture.addClass("package foo;\nimport bar.A;\nfinal class B extends A {}");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B {}");
    assertEmpty(myFixture.filterAvailableIntentions("Move to package 'bar'"));
  }

  private void invokeFix(@NotNull String hint) {
    myFixture.launchAction(myFixture.findSingleIntention(hint));
  }
}

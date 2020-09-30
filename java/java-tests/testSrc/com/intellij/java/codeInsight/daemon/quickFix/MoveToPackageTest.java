// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MoveToPackageTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  public void testSimple() {
    PsiClass bClass = myFixture.addClass("package foo;\nimport bar.A;\npublic final class B extends A {}");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B {}");
    invokeFix("Move to package 'bar'");
    assertEquals("package bar;\nimport bar.A;\npublic final class B extends A {}", bClass.getContainingFile().getText());
  }

  public void testNestedClass() {
    PsiClass bClass = myFixture.addClass("package foo;\nimport bar.A;\npublic class B { public static final class C extends A {} }");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B.C {}");
    invokeFix("Move to package 'bar'");
    assertEquals("package bar;\nimport bar.A;\npublic class B { public static final class C extends A {} }",
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

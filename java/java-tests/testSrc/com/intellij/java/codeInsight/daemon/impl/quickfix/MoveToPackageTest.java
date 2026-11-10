// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class MoveToPackageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass bClass = myFixture.addClass("package foo;\nimport bar.A;\npublic final class B extends A {}");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B {}");
    invokeFix("Move class 'B' to package 'bar'");
    ReadAction.runBlocking(() -> assertEquals("package bar;\n\npublic final class B extends A {}", bClass.getContainingFile().getText()));
  }

  @Test
  public void testNestedClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass bClass = myFixture.addClass("package foo;\nimport bar.A;\npublic class B { public static final class C extends A {} }");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B.C {}");
    invokeFix("Move class 'C' to package 'bar'");
    ReadAction.runBlocking(() -> {
      assertEquals("package bar;\n\npublic class B { public static final class C extends A {} }",
                   bClass.getContainingFile().getText());
    });
  }

  @Test
  public void testNonAccessibleClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo;\nimport bar.A;\nfinal class B extends A {}");
    myFixture.configureByText("A.java", "package bar;\n import foo.B;\npublic sealed class A permits <caret>B {}");
    assertThat(myFixture.filterAvailableIntentions("Move to package 'bar'")).isNullOrEmpty();
  }

  private void invokeFix(@NotNull String hint) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.launchAction(myFixture.findSingleIntention(hint));
  }
}

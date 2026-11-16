// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GotoEnumConstructorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testGotoDeclarationOnEnumConstantDoesntNavigateToEnumClass() {
    doTest(
      "enum A { G<caret>G; }",
      "enum A { G<caret>G; }"
    );
  }

  @Test
  public void testGotoEnumConstructorDeclaration() {
    doTest(
      "enum A { G<caret>; A(){}}",
      "enum A { G; <caret>A(){}}"
    );
  }

  @Test
  public void testGotoEnumConstructorDeclaration2() {
    doTest(
      "enum A { G<caret>(); A(){}}",
      "enum A { G(); <caret>A(){}}"
    );
  }

  @Test
  public void testGotoEnumConstructorDeclaration3() {
    doTest(
      "enum A { G<caret>(42); A(){} A(int x){}}",
      "enum A { G(42); A(){} <caret>A(int x){}}"
    );
  }

  private void doTest(@NotNull String before, @NotNull String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", before);
    myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION);
    myFixture.checkResult(after);
  }
}

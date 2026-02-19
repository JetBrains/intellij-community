// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class GotoEnumConstructorTest extends LightJavaCodeInsightFixtureTestCase {

  public void testGotoDeclarationOnEnumConstantDoesntNavigateToEnumClass() {
    doTest(
      "enum A { G<caret>G; }",
      "enum A { G<caret>G; }"
    );
  }

  public void testGotoEnumConstructorDeclaration() {
    doTest(
      "enum A { G<caret>; A(){}}",
      "enum A { G; <caret>A(){}}"
    );
  }

  public void testGotoEnumConstructorDeclaration2() {
    doTest(
      "enum A { G<caret>(); A(){}}",
      "enum A { G(); <caret>A(){}}"
    );
  }

  public void testGotoEnumConstructorDeclaration3() {
    doTest(
      "enum A { G<caret>(42); A(){} A(int x){}}",
      "enum A { G(42); A(){} <caret>A(int x){}}"
    );
  }

  private void doTest(@NotNull String before, @NotNull String after) {
    myFixture.configureByText("A.java", before);
    myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION);
    myFixture.checkResult(after);
  }
}

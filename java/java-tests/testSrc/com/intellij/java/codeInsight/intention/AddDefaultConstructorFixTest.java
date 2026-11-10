// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class AddDefaultConstructorFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_adding_constructor_to_super_class() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass superClass = myFixture.addClass("abstract class Foo { Foo(int a) {} }");
    myFixture.configureByText("a.java", "class Bar extends Foo { <caret>Bar() { } }");
    boolean madeWritable =
      CodeInsightTestFixtureImpl.withReadOnlyFile(superClass.getContainingFile().getVirtualFile(), myFixture.getProject(),
                                                  () -> myFixture.launchAction(
                                                    myFixture.findSingleIntention("Add package-private no-args constructor")));
    ReadAction.runBlocking(() -> assertEquals(2, superClass.getConstructors().length));
    assertTrue(madeWritable);
  }
}

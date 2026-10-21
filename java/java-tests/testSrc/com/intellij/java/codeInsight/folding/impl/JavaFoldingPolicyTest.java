// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.impl.FoldingPolicy;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaFoldingPolicyTest {
  private static final TestFixture<Path> pathFixture = tempPathFixture();
  private static final TestFixture<Project> projectFixture = projectFixture(pathFixture);
  @SuppressWarnings("unused")
  private static final TestFixture<Module> moduleFixture = moduleFixture(projectFixture, pathFixture, true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightFixture(projectFixture, pathFixture);

  @Test
  public void testAdditionalChildDocComments() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java",
                              """
                                /** outer **/
                                class Test {
                                /** <caret>inner **/
                                }""");
    ReadAction.runBlocking(() -> {
      PsiElement element = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()),
                                                       PsiDocComment.class, false);
      assertNotNull(element);
      String signature = FoldingPolicy.getSignature(element);
      if (signature != null) {
        assertEquals(element, FoldingPolicy.restoreBySignature(element.getContainingFile(), signature));
      }
    });
  }
}

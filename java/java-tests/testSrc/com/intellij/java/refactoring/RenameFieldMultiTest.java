// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class RenameFieldMultiTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameField/multi/");
  }

  @Test
  public void testNonCodeUsages() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("""
                                           package p;
                                           public class A {
                                             private String myField;
                                           }""");
    doTest(aClass, "properties");
  }

  private void doTest(PsiClass aClass, String ext) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String suffix = myFixture.getTestName(false);
    myFixture.configureByFile("before" + suffix + "." + ext);
    EdtTestUtil.runInEdtAndWait(() -> {
      rename(aClass.findFieldByName("myField", false), "myField1");
    });
    myFixture.checkResultByFile("after" + suffix + "." + ext);
  }

  /** Renames {@code element}, searching in comments and in text occurrences; a subclass renames with another engine. */
  protected void rename(@NotNull PsiElement element, @NotNull String newName) {
    new RenameProcessor(element.getProject(), element, newName, true, true).run();
  }
}

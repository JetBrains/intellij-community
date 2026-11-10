// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class FilePathClassNameCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/filePath2/");
  }

  @Test
  public void testJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("src1/i/i2/717_image.png", "i/i2/717_image.png");
    myFixture.configureByFile("src1/Test.java");
    LookupElement[] myItems = complete();

    assertEquals(1, myItems.length);
    assertEquals("717_image.png (16x16)", myItems[0].toString());

    final LookupElement element = myItems[0];
    assertInstanceOf(PsiElement.class, element.getObject());

    myFixture.type('\t');
    checkResultByFile("src1_after/Test.java");
  }

  private void checkResultByFile(String path) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.checkResultByFile(path);
  }

  @Test
  public void testJava_wrongCase() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("fake_file.txt", "");
    myFixture.configureByFile("src3/TestWrongCase.java");
    LookupElement[] myItems = complete();

    assertEquals(1, myItems.length);
    assertEquals("fake_file.txt", myItems[0].getLookupString());

    final LookupElement element = myItems[0];
    assertInstanceOf(PsiElement.class, element.getObject());

    myFixture.type('\t');
    checkResultByFile("src3_after/TestWrongCase.java");
  }

  @Test
  public void testFileReferenceVariantsFiltered() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo.txt", "");
    myFixture.configureByText("FooBar.java", "class FooBar { String barName = \"foo<caret>\"}");
    complete();

    myFixture.checkResult("class FooBar { String barName = \"foo.txt\"}");
  }

  private LookupElement[] complete() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return myFixture.complete(CompletionType.BASIC, 2);
  }

  @Test
  public void testUnblockDocument() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("i/i2/717_image.png", "");
    myFixture.configureByFile("src1/Test.java");
    complete();

    myFixture.type("7_image.png ");

    checkResultByFile("src1_after/UnblockDocument.java");
  }
}
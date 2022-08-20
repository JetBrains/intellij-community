// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author spleaner
 */
public class FilePathClassNameCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/filePath2/";
  }

  public void testJava() {
    myFixture.copyFileToProject("src1/i/i2/717_image.png", "i/i2/717_image.png");
    myFixture.configureByFile("src1/Test.java");
    LookupElement[] myItems = complete();

    assertEquals(1, myItems.length);
    assertEquals("717_image.png (16x16)", myItems[0].toString());

    final LookupElement element = myItems[0];
    assertInstanceOf(element.getObject(), PsiElement.class);

    myFixture.type('\t');
    checkResultByFile("src1_after/Test.java");
  }

  private void checkResultByFile(String path) {
    myFixture.checkResultByFile(path);
  }

  public void testJava_wrongCase() {
    myFixture.addFileToProject("fake_file.txt", "");
    myFixture.configureByFile("src3/TestWrongCase.java");
    LookupElement[] myItems = complete();

    assertEquals(1, myItems.length);
    assertEquals("fake_file.txt", myItems[0].getLookupString());

    final LookupElement element = myItems[0];
    assertInstanceOf(element.getObject(), PsiElement.class);

    myFixture.type('\t');
    checkResultByFile("src3_after/TestWrongCase.java");
  }

  public void testFileReferenceVariantsFiltered() {
    myFixture.addFileToProject("foo.txt", "");
    myFixture.configureByText("FooBar.java", "class FooBar { String barName = \"foo<caret>\"}");
    complete();

    myFixture.checkResult("class FooBar { String barName = \"foo.txt\"}");
  }

  private LookupElement[] complete() {
    return myFixture.complete(CompletionType.BASIC, 2);
  }

  public void testUnblockDocument() {
    myFixture.addFileToProject("i/i2/717_image.png", "");
    myFixture.configureByFile("src1/Test.java");
    complete();

    myFixture.type("7_image.png ");

    checkResultByFile("src1_after/UnblockDocument.java");
  }
}
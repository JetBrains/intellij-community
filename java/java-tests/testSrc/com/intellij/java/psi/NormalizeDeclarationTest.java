// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

@HeavyPlatformTestCase.WrapInCommand
public class NormalizeDeclarationTest extends JavaPsiTestCase {
  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/normalizeDeclaration";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  public void test1() throws Exception { doTest(); }
  public void test2() throws Exception { doTest(); }
  public void testCStyleArrayParameterBeforeErrorId() throws Exception { doTest(); }
  public void testForLoopInitializer() throws Exception { doTest(); }

  public void testSCR6549() throws Exception { doTest(); }
  public void testSCR9467() throws Exception { doTest(); }
  public void testSCR9467_1() throws Exception { doTest(); }

  private void doTest() throws Exception {
    PsiElement element = configureByFileWithMarker(BASE_PATH + "/" + getTestName(false) + ".java", "<var>");
    assertTrue(element instanceof PsiIdentifier);
    assertTrue(element.getParent() instanceof PsiVariable);

    ApplicationManager.getApplication().runWriteAction(() -> ((PsiVariable)element.getParent()).normalizeDeclaration());


    String textAfter = loadFile(getTestName(false) + "_after.java");
    ApplicationManager.getApplication().runWriteAction(() -> PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting());

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    String fileText = myFile.getText();
    assertEquals(textAfter, fileText);
    PsiTestUtil.checkFileStructure(myFile);
  }

}

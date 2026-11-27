// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class NormalizeDeclarationTest {
  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/normalizeDeclaration";

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(BASE_PATH);

  @Test public void test1() { doTest(); }
  @Test public void test2() { doTest(); }
  @Test public void testCStyleArrayParameterBeforeErrorId() { doTest(); }
  @Test public void testForLoopInitializer() { doTest(); }

  @Test public void testSCR6549() { doTest(); }
  @Test public void testSCR9467() { doTest(); }
  @Test public void testSCR9467_1() { doTest(); }

  private void doTest() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    PsiElement element = fixture.configureByFileWithMarker("/" + fixture.getTestName(false) + ".java", "<var>");
    assertTrue(element instanceof PsiIdentifier);
    assertTrue(ReadAction.computeBlocking(() -> element.getParent()) instanceof PsiVariable);

    WriteCommandAction.writeCommandAction(project).run(() -> ((PsiVariable)element.getParent()).normalizeDeclaration());


    String textAfter = fixture.loadFile(fixture.getTestName(false) + "_after.java");
    WriteCommandAction.writeCommandAction(project).run(() -> PostprocessReformattingAspect.getInstance(project).doPostponedFormatting());

    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());

    PsiFile myFile = fixture.getFile();
    String fileText = ReadAction.computeBlocking(() -> myFile.getText());
    assertEquals(textAfter, fileText);
    ReadAction.runBlocking(() -> PsiTestUtil.checkFileStructure(myFile));
  }

}

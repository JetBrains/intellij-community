// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.ide.scratch.ScratchFileCreationHelper;
import com.intellij.ide.scratch.ScratchFileCreator;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public final class LightScratchHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSealedHierarchy() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_21));
    String text = """
      sealed interface Cacheable permits Result {
        default void clear() {
          System.out.println("clearing cache...");
        }
      }
      sealed abstract class Result implements Cacheable permits IntResult {
      }
      final class IntResult extends Result {
      }""";
    var fileCreator = new Runnable() {
      private PsiFile myFile;

      @Override
      public void run() {
        ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
        context.language = JavaLanguage.INSTANCE;
        myFile = ScratchFileCreator.doCreateNewScratch(myFixture.getProject(), context);
        Document document = myFile.getFileDocument();
        document.insertString(0, text);
        PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(document);
      }
    };
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), fileCreator);
    PsiFile file = fileCreator.myFile;
    ReadAction.runBlocking(() -> {
      JavaErrorCollector collector = new JavaErrorCollector(file, e -> fail(e.description()));
      PsiTreeUtil.processElements(file, element -> {
        collector.processElement(element);
        return true;
      });
    });
  }
}

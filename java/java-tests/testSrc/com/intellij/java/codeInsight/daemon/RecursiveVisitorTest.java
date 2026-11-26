// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.LocalTimeCounter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class RecursiveVisitorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testStopWalking() {
    @Language("JAVA")
    String text = """
      class Test {
        Test() {
          super();
          super();
          {
            super();
          }
        }
      }""";
    int[] count = {0};
    ReadAction.runBlocking(() -> {
      FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName("Test.java");
      PsiJavaFile psiJavaFile = (PsiJavaFile)PsiFileFactory.getInstance(daemonFixture.get().getFixture().getProject())
        .createFileFromText("Test.java", fileType, text, LocalTimeCounter.currentTime(), true, false);
      PsiCodeBlock body = psiJavaFile.getClasses()[0].getMethods()[0].getBody();
      body.acceptChildren(new JavaRecursiveElementWalkingVisitor() {

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
          count[0]++;
          stopWalking();
        }
      });
    });
    assertEquals(1, count[0]);
  }
}

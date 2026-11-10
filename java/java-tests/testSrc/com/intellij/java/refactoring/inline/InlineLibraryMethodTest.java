// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author anna
 */
@TestApplication
public class InlineLibraryMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  @Test
  public void testInlineAllInProjectFromLibrary() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package mycompany;
                         import java.util.*; // unused
                         public class File {
                           @IntrinsicCandidate
                           public static File createTempFile(String pr, String postfix) { return createTempFile(pr, postfix, null); }
                           public static File createTempFile(String pr, String postfix, String base) { return new File(); }
                         }
                         @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
                         @Retention(RetentionPolicy.RUNTIME)
                         @interface IntrinsicCandidate {}""");
    @NonNls String fileName = "/refactoring/inlineMethod/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(fileName);

    PsiClass libraryClass = ReadAction.computeBlocking(() -> myFixture.findClass("mycompany.File"));

    final PsiFile file = ReadAction.computeBlocking(() -> libraryClass.getContainingFile());
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        file.getVirtualFile().setWritable(false);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    PsiMethod methodToInline = ReadAction.computeBlocking(() -> {
      PsiMethod[] createTempFiles = libraryClass.findMethodsByName("createTempFile", false);
      return ContainerUtil.find(createTempFiles, createTempFile -> createTempFile.getParameterList().getParametersCount() == 2);
    });
    assertNotNull(methodToInline);
    final boolean condition = ReadAction.computeBlocking(() -> InlineMethodProcessor.checkBadReturns(methodToInline) && !InlineUtil.allUsagesAreTailCalls(methodToInline));
    assertFalse(condition, "Bad returns found");
    EdtTestUtil.runInEdtAndWait(() -> {
      final InlineMethodProcessor processor = new InlineMethodProcessor(myFixture.getProject(), methodToInline, null, myFixture.getEditor(), false, false, false, false);
      processor.run();
      myFixture.checkResultByFile(fileName + ".after");
    });
  }
}

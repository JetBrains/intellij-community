// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * @author anna
 */
public class InlineLibraryMethodTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInlineAllInProjectFromLibrary() {
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
    @NonNls String fileName = "/refactoring/inlineMethod/" + getTestName(false) + ".java";
    myFixture.configureByFile(fileName);

    PsiClass libraryClass = myFixture.findClass("mycompany.File");

    final PsiFile file = libraryClass.getContainingFile();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        file.getVirtualFile().setWritable(false);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    PsiMethod[] createTempFiles = libraryClass.findMethodsByName("createTempFile", false);
    PsiMethod methodToInline =
      ContainerUtil.find(createTempFiles, createTempFile -> createTempFile.getParameterList().getParametersCount() == 2);
    assertNotNull(methodToInline);
    final boolean condition = InlineMethodProcessor.checkBadReturns(methodToInline) && !InlineUtil.allUsagesAreTailCalls(methodToInline);
    assertFalse("Bad returns found", condition);
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), methodToInline, null, getEditor(), false, false, false, false);
    processor.run();
    myFixture.checkResultByFile(fileName + ".after");
  }
}

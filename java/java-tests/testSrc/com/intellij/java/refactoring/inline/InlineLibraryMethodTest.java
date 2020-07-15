// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
    myFixture.addClass("package mycompany;\n" +
                                           "public class File {\n" +
                                           " public static File createTempFile(String pr, String postfix){return createTempFile(pr, postfix, null);}\n" +
                                           " public static File createTempFile(String pr, String postfix, String base){return new File();}\n" +
                                           "}");
    @NonNls String fileName = "/refactoring/inlineMethod/" + getTestName(false) + ".java";
    myFixture.configureByFile(fileName);

    PsiClass fileClass = myFixture.findClass("mycompany.File");

    final PsiFile file = fileClass.getContainingFile();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        file.getVirtualFile().setWritable(false);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    PsiMethod element = null;
    PsiMethod[] createTempFiles = fileClass.findMethodsByName("createTempFile", false);
    for (PsiMethod createTempFile : createTempFiles) {
      if (createTempFile.getParameterList().getParametersCount() == 2) {
        element = createTempFile;
        break;
      }
    }
    assertNotNull(element);
    PsiMethod method = element;
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), method, null, getEditor(), false);
    processor.run();
    myFixture.checkResultByFile(fileName + ".after");
  }
}

/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.psi.*;
import com.intellij.refactoring.util.InlineUtil;
import org.jetbrains.annotations.NonNls;

/**
 * User: anna
 * Date: 11/4/11
 */
public class InlineLibraryMethodTest extends CodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInlineAllInProjectFromLibrary() throws Exception {
    configureByText(JavaFileType.INSTANCE, "package mycompany;\n" +
                                           "public class File {\n" +
                                           " public static File createTempFile(String pr, String postfix){return createTempFile(pr, postfix, null);}\n" +
                                           " public static File createTempFile(String pr, String postfix, String base){return new File();}\n" +
                                           "}");
    @NonNls String fileName = "/refactoring/inlineMethod/" + getTestName(false) + ".java";
    configureByFile(fileName);

    PsiClass fileClass = JavaPsiFacade.getInstance(getProject()).findClass("mycompany.File");
    assertTrue(fileClass != null);
    PsiFile file = fileClass.getContainingFile();
    ((VirtualFileSystemEntry)file.getVirtualFile()).setWritable(false);
    PsiElement element = null;
    PsiMethod[] createTempFiles = fileClass.findMethodsByName("createTempFile", false);
    for (PsiMethod createTempFile : createTempFiles) {
      if (createTempFile.getParameterList().getParametersCount() == 2) {
        element = createTempFile;
        break;
      }
    }
    assertTrue(element != null);
    PsiMethod method = (PsiMethod)element;
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), method, null, myEditor, false);
    processor.run();
    checkResultByFile(fileName + ".after");
  }
}

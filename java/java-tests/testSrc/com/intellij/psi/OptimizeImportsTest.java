/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;

public class OptimizeImportsTest extends PsiTestCase{
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/optimizeImports";

  public void testSCR6138() throws Exception { doTest(); }
  public void testSCR18364() throws Exception { doTest(); }
  public void testStaticImports1() throws Exception { doTest(); }
  public void testStaticImportsToOptimize() throws Exception { doTest(); }
  public void testStaticImportsToOptimize2() throws Exception { doTest(); }
  public void testEmptyImportList() throws Exception { doTest(); }
  public void testIDEADEV10716() throws Exception { doTest(); }
  public void testUnresolvedImports() throws Exception { doTest(); }
  public void testUnresolvedImports2() throws Exception { doTest(); }
  public void testNewImportListIsEmptyAndCommentPreserved() throws Exception { doTest(); }
  public void testNewImportListIsEmptyAndJavaDocWithInvalidCodePreserved() throws Exception { doTest(); }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("mock 1.5");
  }

  private void doTest() throws Exception {
    final String extension = ".java";
    doTest(extension);
  }

  private void doTest(final String extension) throws Exception {
    CommandProcessor.getInstance().executeCommand(
      getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            String fileName = getTestName(false) + extension;
            try {
              String text = loadFile(fileName);
              PsiFile file = createFile(fileName, text);

              JavaCodeStyleManager.getInstance(myProject).optimizeImports(file);
              PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
              PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
              String textAfter = loadFile(getTestName(false) + "_after" + extension);
              String fileText = file.getText();
              assertEquals(textAfter, fileText);
            }
            catch (Exception e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "", "");

  }

  private static String loadFile(String name) throws Exception {
    String fullName = BASE_PATH + File.separatorChar + name;
    String text = new String(FileUtil.loadFileText(new File(fullName)));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}

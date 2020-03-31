/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

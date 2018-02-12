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

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PsiTestCase;

/**
 * @author Dmitry Avdeev
 */
public abstract class OptimizeImportsTestCase extends PsiTestCase {
  protected void doTest(final String extension) {
    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
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
      }), "", "");

  }
}

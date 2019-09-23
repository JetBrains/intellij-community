/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testIntegration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testIntegration.createTest.CreateTestAction;
import com.intellij.util.IncorrectOperationException;

public class JavaTestCreator implements TestCreator {
  private static final Logger LOG = Logger.getInstance(JavaTestCreator.class);

  @Override
  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = findElement(file, offset);
    return CreateTestAction.isAvailableForElement(element);
  }

  @Override
  public void createTest(Project project, Editor editor, PsiFile file) {
    try {
      CreateTestAction action = new CreateTestAction();
      PsiElement element = findElement(file, editor.getCaretModel().getOffset());
      if (CreateTestAction.isAvailableForElement(element)) {
        action.invoke(project, editor, element);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.warn(e);
    }
  }

  private static PsiElement findElement(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null && offset == file.getTextLength()) {
      element = file.findElementAt(offset - 1);
    }
    return element;
  }
}

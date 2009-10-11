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

/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.extractMethodObject;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObjectHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#" + ExtractMethodObjectHandler.class.getName());

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, new Pass<PsiElement[]>() {
      public void pass(final PsiElement[] selectedValue) {
        invokeOnElements(project, editor, file, selectedValue);
      }
    });
  }

  private void invokeOnElements(final Project project, final Editor editor, PsiFile file, PsiElement[] elements) {
    if (elements.length == 0) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, ExtractMethodObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD_OBJECT);
      return;
    }

    final ExtractMethodObjectProcessor processor = new ExtractMethodObjectProcessor(project, editor, elements, "");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    try {
      if (!extractProcessor.prepare()) return;
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), ExtractMethodObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD_OBJECT);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, extractProcessor.getTargetClass().getContainingFile())) return;

    if (extractProcessor.showDialog()) {
      new WriteCommandAction(project, ExtractMethodObjectProcessor.REFACTORING_NAME, ExtractMethodObjectProcessor.REFACTORING_NAME) {
        protected void run(final Result result) throws Throwable {
          extractProcessor.doRefactoring();
          processor.run();
          if (processor.isCreateInnerClass()) {

            processor.moveUsedMethodsToInner();

            DuplicatesImpl.processDuplicates(extractProcessor, project, editor);
            processor.changeInstanceAccess(project);
          }
          final PsiElement method = processor.getMethod();
          LOG.assertTrue(method != null);
          method.delete();
        }
      }.execute();
    }
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}
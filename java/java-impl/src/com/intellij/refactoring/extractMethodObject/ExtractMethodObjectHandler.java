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

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObjectHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance("#" + ExtractMethodObjectHandler.class.getName());

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, new Pass<PsiElement[]>() {
      public void pass(final PsiElement[] selectedValue) {
        invokeOnElements(project, editor, file, selectedValue);
      }
    });
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final PsiElement[] elements = ExtractMethodHandler.getElements(file.getProject(), editor, file);
    return elements != null && elements.length > 0;
  }

  private static void invokeOnElements(@NotNull final Project project,
                                       @NotNull final Editor editor,
                                       @NotNull PsiFile file,
                                       @NotNull PsiElement[] elements) {
    if (elements.length == 0) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, ExtractMethodObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD_OBJECT);
      return;
    }

    try {
      extractMethodObject(project, editor, new ExtractMethodObjectProcessor(project, editor, elements, ""));
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), ExtractMethodObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD_OBJECT);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
    }
  }

  static void extractMethodObject(Project project, Editor editor, ExtractMethodObjectProcessor processor) throws PrepareFailedException {
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    if (!extractProcessor.prepare()) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, extractProcessor.getTargetClass().getContainingFile())) return;
    if (extractProcessor.showDialog()) {
      run(project, editor, processor, extractProcessor);
    }
  }

  public static void run(@NotNull final Project project,
                           final Editor editor,
                  @NotNull final ExtractMethodObjectProcessor processor,
                  @NotNull final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor) {
    final RangeMarker marker;
    if (editor != null) {
      final int offset = editor.getCaretModel().getOffset();
      marker = editor.getDocument().createRangeMarker(new TextRange(offset, offset));
    } else {
      marker = null;
    }
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
          public void run() {
            try {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  extractProcessor.doRefactoring();
                }
              });
              processor.run();
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  processor.runChangeSignature();
                }
              });
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }

            PsiDocumentManager.getInstance(project).commitAllDocuments();
            if (processor.isCreateInnerClass()) {
              processor.moveUsedMethodsToInner();
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              if (editor != null) {
                DuplicatesImpl.processDuplicates(extractProcessor, project, editor);
              }
            }
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                if (processor.isCreateInnerClass()) {
                  processor.changeInstanceAccess(project);
                }
                final PsiElement method = processor.getMethod();
                LOG.assertTrue(method != null);
                method.delete();
              }
            });
          }
        });
      }
    }, ExtractMethodObjectProcessor.REFACTORING_NAME, ExtractMethodObjectProcessor.REFACTORING_NAME);
    if (editor != null) {
      editor.getCaretModel().moveToOffset(marker.getStartOffset());
      marker.dispose();
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}
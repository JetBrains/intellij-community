// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.extractMethodObject;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
  private static final Logger LOG = Logger.getInstance(ExtractMethodObjectHandler.class);

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, selectedValue-> invokeOnElements(project, editor, file, selectedValue));
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    return false;
  }

  private static void invokeOnElements(final @NotNull Project project,
                                       final @NotNull Editor editor,
                                       @NotNull PsiFile file,
                                       PsiElement @NotNull [] elements) {
    if (elements.length == 0) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, JavaRefactoringBundle.message("extract.method.object"), HelpID.EXTRACT_METHOD_OBJECT);
      return;
    }

    try {
      extractMethodObject(project, editor, new ExtractMethodObjectProcessor(project, editor, elements, ""));
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), JavaRefactoringBundle.message("extract.method.object"), HelpID.EXTRACT_METHOD_OBJECT);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
    }
  }

  static void extractMethodObject(Project project, Editor editor, ExtractMethodObjectProcessor processor) throws PrepareFailedException {
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    if (!extractProcessor.prepare()) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, extractProcessor.getTargetClass().getContainingFile())) return;
    if (extractProcessor.showDialog()) {
      extractMethodObject(project, editor, processor, extractProcessor);
    }
  }

  public static void extractMethodObject(final @NotNull Project project,
                                         final Editor editor,
                                         final @NotNull ExtractMethodObjectProcessor processor,
                                         final @NotNull ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor) {
    final RangeMarker marker;
    if (editor != null) {
      final int offset = editor.getCaretModel().getOffset();
      marker = editor.getDocument().createRangeMarker(offset, offset);
    } else {
      marker = null;
    }
    CommandProcessor.getInstance().executeCommand(project,
                                                  () -> doRefactoring(project, editor, processor, extractProcessor),
                                                  JavaRefactoringBundle.message("extract.method.object"),
                                                  JavaRefactoringBundle.message("extract.method.object"));
    if (editor != null) {
      editor.getCaretModel().moveToOffset(marker.getStartOffset());
      marker.dispose();
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private static void doRefactoring(@NotNull Project project,
                                    Editor editor,
                                    @NotNull ExtractMethodObjectProcessor processor,
                                    @NotNull ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor) {
    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
      try {
        ApplicationManager.getApplication().runWriteAction(() -> extractProcessor.doRefactoring());
        processor.run();
        ApplicationManager.getApplication().runWriteAction(() -> processor.runChangeSignature());
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
      ApplicationManager.getApplication().runWriteAction(() -> {
        if (processor.isCreateInnerClass()) {
          processor.changeInstanceAccess(project);
        }
        final PsiElement method = processor.getMethod();
        LOG.assertTrue(method != null);
        method.delete();
      });
    });
  }

  @Override
  public void invoke(final @NotNull Project project, final PsiElement @NotNull [] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}
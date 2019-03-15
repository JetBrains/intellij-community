// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class ExtractMethodWithResultObjectHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(ExtractMethodWithResultObjectHandler.class);

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiElement[] elements = ExtractMethodHandler.getElements(file.getProject(), editor, file);
    return !ArrayUtil.isEmpty(elements);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, new Pass<PsiElement[]>() {
      @Override
      public void pass(PsiElement[] selectedValue) {
        invokeOnElements(project, editor, file, selectedValue);
      }
    });
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  private static void invokeOnElements(@NotNull Project project,
                                       @NotNull Editor editor,
                                       @NotNull PsiFile file,
                                       @NotNull PsiElement[] elements) {
    if (elements.length == 0) {
      String message = RefactoringBundle
        .getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
      CommonRefactoringUtil
        .showErrorHint(project, editor, message, ExtractMethodWithResultObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD);
      return;
    }

    try {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, elements[0].getContainingFile())) return;
      extractMethodWithResultObject(new ExtractMethodWithResultObjectProcessor(project, editor, elements));
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil
        .showErrorHint(project, editor, e.getMessage(), ExtractMethodWithResultObjectProcessor.REFACTORING_NAME, HelpID.EXTRACT_METHOD);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
    }
  }

  static void extractMethodWithResultObject(ExtractMethodWithResultObjectProcessor processor)
    throws PrepareFailedException {
    if (!processor.prepare()) return;
    if (processor.showDialog()) {
      extractMethodWithResultObjectImpl(processor);
    }
  }

  public static void extractMethodWithResultObjectImpl(@NotNull ExtractMethodWithResultObjectProcessor processor) {
    RangeMarker marker = createMarker(processor.getEditor());
    try {
      CommandProcessor.getInstance().executeCommand(processor.getProject(),
                                                    () -> doRefactoring(processor),
                                                    ExtractMethodWithResultObjectProcessor.REFACTORING_NAME,
                                                    ExtractMethodWithResultObjectProcessor.REFACTORING_NAME);
    }
    finally {
      if (marker != null) {
        putCaretToMarker(processor.getEditor(), marker);
      }
    }
  }

  @Contract("null->null; !null->!null")
  @Nullable
  private static RangeMarker createMarker(Editor editor) {
    if (editor != null) {
      int offset = editor.getCaretModel().getOffset();
      return editor.getDocument().createRangeMarker(offset, offset);
    }
    return null;
  }

  private static void putCaretToMarker(@NotNull Editor editor, @NotNull RangeMarker marker) {
    editor.getCaretModel().moveToOffset(marker.getStartOffset());
    marker.dispose();
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static void doRefactoring(ExtractMethodWithResultObjectProcessor processor) {
    PostprocessReformattingAspect.getInstance(processor.getProject()).postponeFormattingInside(() -> {
      try {
        ApplicationManager.getApplication().runWriteAction(() -> processor.doRefactoring());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }
}

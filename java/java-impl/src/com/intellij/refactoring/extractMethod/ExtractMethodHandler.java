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
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExtractMethodHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractMethod.ExtractMethodHandler");

  public static final String REFACTORING_NAME = RefactoringBundle.message("extract.method.title");

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (dataContext != null) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (file != null && editor != null) {
        invokeOnElements(project, editor, file, elements);
      }
    }
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final Pass<PsiElement[]> callback = new Pass<PsiElement[]>() {
      public void pass(final PsiElement[] selectedValue) {
        invokeOnElements(project, editor, file, selectedValue);
      }
    };
    selectAndPass(project, editor, file, callback);
  }

  public static void selectAndPass(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final Pass<PsiElement[]> callback) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!editor.getSelectionModel().hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      final List<PsiExpression> expressions = IntroduceVariableBase.collectExpressions(file, editor, offset, true);
      if (expressions.isEmpty()) {
        editor.getSelectionModel().selectLineAtCaret();
      }
      else if (expressions.size() == 1) {
        callback.pass(new PsiElement[]{expressions.get(0)});
        return;
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions, new Pass<PsiExpression>() {
          @Override
          public void pass(PsiExpression psiExpression) {
            callback.pass(new PsiElement[]{psiExpression});
          }
        }, new PsiExpressionTrimRenderer.RenderFunction());
        return;
      }
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    callback.pass(getElements(project, editor, file));
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final PsiElement[] elements = getElements(file.getProject(), editor, file);
    return elements != null && elements.length > 0;
  }

  public static PsiElement[] getElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int startOffset = selectionModel.getSelectionStart();
      int endOffset = selectionModel.getSelectionEnd();


      PsiElement[] elements;
      PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
      if (expr != null) {
        elements = new PsiElement[]{expr};
      }
      else {
        elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
        if (elements.length == 0) {
          final PsiExpression expression = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset);
          if (expression != null && IntroduceVariableBase.getErrorMessage(expression) == null) {
            final PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expression);
            if (originalType != null) {
              elements = new PsiElement[]{expression};
            }
          }
        }
      }
      return elements;
    }

    final List<PsiExpression> expressions = IntroduceVariableBase.collectExpressions(file, editor, editor.getCaretModel().getOffset());
    return expressions.toArray(new PsiElement[expressions.size()]);
  }

  private static void invokeOnElements(final Project project, final Editor editor, PsiFile file, PsiElement[] elements) {
    getProcessor(elements, project, file, editor, true, new Pass<ExtractMethodProcessor>(){
      @Override
      public void pass(ExtractMethodProcessor processor) {
        invokeOnElements(project, editor, processor, true);
      }
    });
  }

  private static boolean invokeOnElements(final Project project, final Editor editor, @NotNull final ExtractMethodProcessor processor, final boolean directTypes) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, processor.getTargetClass().getContainingFile())) return false;
    if (processor.showDialog(directTypes)) {
      extractMethod(project, processor);
      DuplicatesImpl.processDuplicates(processor, project, editor);
      return true;
    }
    return false;
  }

  public static void extractMethod(@NotNull final Project project, final ExtractMethodProcessor processor) {
    CommandProcessor.getInstance().executeCommand(project,
                                                  () -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
                                                    () -> doRefactoring(project, processor)), REFACTORING_NAME, null);
  }

  private static void doRefactoring(@NotNull Project project, ExtractMethodProcessor processor) {
    try {
      final RefactoringEventData beforeData = new RefactoringEventData();
      beforeData.addElements(processor.myElements);
      project.getMessageBus().syncPublisher(
        RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted("refactoring.extract.method", beforeData);

      processor.doRefactoring();

      final RefactoringEventData data = new RefactoringEventData();
      data.addElement(processor.getExtractedMethod());
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .refactoringDone("refactoring.extract.method", data);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static ExtractMethodProcessor getProcessor(final PsiElement[] elements,
                                                     final Project project,
                                                     final PsiFile file,
                                                     final Editor editor,
                                                     final boolean showErrorMessages,
                                                     final @Nullable Pass<ExtractMethodProcessor> pass) {
    if (elements == null || elements.length == 0) {
      if (showErrorMessages) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_METHOD);
      }
      return null;
    }

    for (PsiElement element : elements) {
      if (element instanceof PsiStatement && JavaHighlightUtil.isSuperOrThisCall((PsiStatement)element, true, true)) {
        if (showErrorMessages) {
          String message = RefactoringBundle
            .getCannotRefactorMessage(RefactoringBundle.message("selected.block.contains.invocation.of.another.class.constructor"));
          CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_METHOD);
        }
        return null;
      }
    }

    final ExtractMethodProcessor processor =
      new ExtractMethodProcessor(project, editor, elements, null, REFACTORING_NAME, "", HelpID.EXTRACT_METHOD);
    processor.setShowErrorDialogs(showErrorMessages);
    try {
      if (!processor.prepare(pass)) return null;
    }
    catch (PrepareFailedException e) {
      if (showErrorMessages) {
        CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), REFACTORING_NAME, HelpID.EXTRACT_METHOD);
        highlightPrepareError(e, file, editor, project);
      }
      return null;
    }
    return processor;
  }

  public static void highlightPrepareError(PrepareFailedException e, PsiFile file, Editor editor, final Project project) {
    if (e.getFile() == file) {
      final TextRange textRange = e.getTextRange();
      final HighlightManager highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, true, null);
      final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(textRange.getStartOffset());
      editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    }
  }

  @Nullable
  public static ExtractMethodProcessor getProcessor(final Project project,
                                                    final PsiElement[] elements,
                                                    final PsiFile file,
                                                    final boolean openEditor) {
    return getProcessor(elements, project, file, openEditor ? openEditor(file) : null, false, null);
  }

  public static boolean invokeOnElements(final Project project, @NotNull final ExtractMethodProcessor processor, final PsiFile file, final boolean directTypes) {
    return invokeOnElements(project, openEditor(file), processor, directTypes);
  }

  @Nullable
  public static Editor openEditor(@NotNull final PsiFile file) {
    final Project project = file.getProject();
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(project, virtualFile);
    return FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, false);
  }
}

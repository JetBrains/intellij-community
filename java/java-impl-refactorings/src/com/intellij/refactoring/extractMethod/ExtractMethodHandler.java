// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.java.refactoring.JavaRefactoringBundle;
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper;
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor;
import com.intellij.refactoring.extractMethod.preview.ExtractMethodPreviewManager;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ExtractMethodHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(ExtractMethodHandler.class);

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (dataContext != null) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (file != null && editor != null) {
        invokeOnElements(project, editor, file, elements);
      }
    }
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final Pass<PsiElement[]> callback = new Pass<>() {
      @Override
      public void pass(final PsiElement[] selectedValue) {
        invokeOnElements(project, editor, file, selectedValue);
      }
    };
    selectAndPass(project, editor, file, callback);
  }

  public static void
  selectAndPass(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final Pass<PsiElement[]> callback) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!editor.getSelectionModel().hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      final List<PsiExpression> expressions = CommonJavaRefactoringUtil.collectExpressions(file, editor, offset, true);
      if (expressions.isEmpty()) {
        editor.getSelectionModel().selectLineAtCaret();
      }
      else if (expressions.size() == 1) {
        callback.pass(new PsiElement[]{expressions.get(0)});
        return;
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions, new Pass<>() {
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
          final PsiExpression expression = IntroduceVariableUtil.getSelectedExpression(project, file, startOffset, endOffset);
          if (expression != null && IntroduceVariableUtil.getErrorMessage(expression) == null) {
            final PsiType originalType = CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(expression);
            if (originalType != null) {
              elements = new PsiElement[]{expression};
            }
          }
        }
      }
      return elements;
    }

    final List<PsiExpression> expressions = CommonJavaRefactoringUtil.collectExpressions(file, editor, editor.getCaretModel().getOffset());
    return expressions.toArray(PsiElement.EMPTY_ARRAY);
  }

  public static boolean canUseNewImpl(@NotNull Project project, PsiFile file, PsiElement @NotNull [] elements){
    final ExtractMethodProcessor processor = getProcessor(project, elements, file, false);
    if (processor == null) return true;
    try {
      processor.prepare(null);
      processor.prepareVariablesAndName();
      processor.myMethodName = "extracted";
    }
    catch (PrepareFailedException e) {
      return true;
    }

    return processor.estimateDuplicatesCount() == 0 && !processor.myInputVariables.isFoldable();
  }

  public static void invokeOnElements(@NotNull Project project, final Editor editor, PsiFile file, PsiElement @NotNull [] elements) {
    TextRange selection = ExtractMethodHelper.findEditorSelection(editor);
    if (selection == null && elements.length == 1) selection = elements[0].getTextRange();
    if (selection != null) new MethodExtractor().doExtract(file, selection);
  }

  private static boolean invokeOnElements(@NotNull Project project, @NotNull Editor editor, @NotNull ExtractMethodProcessor processor, final boolean directTypes) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, processor.getTargetClass().getContainingFile())) return false;

    processor.setPreviewSupported(true);
    if (processor.showDialog(directTypes)) {
      if (processor.isPreviewDuplicates()) {
        previewExtractMethod(processor);
        return true;
      }
      extractMethod(project, processor);
      DuplicatesImpl.processDuplicates(processor, project, editor);
      return true;
    }
    return false;
  }

  private static void previewExtractMethod(@NotNull ExtractMethodProcessor processor) {
    processor.previewRefactoring(null);
    ExtractMethodPreviewManager.getInstance(processor.getProject()).showPreview(processor);
  }

  public static void extractMethod(@NotNull Project project, @NotNull ExtractMethodProcessor processor) {
    CommandProcessor.getInstance().executeCommand(project,
                                                  () -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
                                                    () -> doRefactoring(project, processor)), getRefactoringName(), null);
  }

  private static void doRefactoring(@NotNull Project project, @NotNull ExtractMethodProcessor processor) {
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
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_METHOD);
      }
      return null;
    }

    for (PsiElement element : elements) {
      if (element instanceof PsiStatement statement && JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
        if (showErrorMessages) {
          String message = RefactoringBundle
            .getCannotRefactorMessage(JavaRefactoringBundle.message("selected.block.contains.invocation.of.another.class.constructor"));
          CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_METHOD);
        }
        return null;
      }
      if (element instanceof PsiStatement && PsiTreeUtil.getParentOfType(element, PsiClass.class) == null) {
        if (showErrorMessages) {
          String message = RefactoringBundle
            .getCannotRefactorMessage(JavaRefactoringBundle.message("selected.block.contains.statement.outside.of.class"));
          CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.EXTRACT_METHOD);
        }
        return null;
      }
    }

    String initialMethodName = Optional.ofNullable(ExtractMethodSnapshot.SNAPSHOT_KEY.get(file)).map(s -> s.myMethodName).orElse("");
    final ExtractMethodProcessor processor =
      new ExtractMethodProcessor(project, editor, elements, null, getRefactoringName(), initialMethodName, HelpID.EXTRACT_METHOD);
    processor.setShowErrorDialogs(showErrorMessages);
    try {
      if (!processor.prepare(pass)) return null;
    }
    catch (PrepareFailedException e) {
      if (showErrorMessages) {
        CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), getRefactoringName(), HelpID.EXTRACT_METHOD);
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
      highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), 
                                         EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
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

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("extract.method.title");
  }
}

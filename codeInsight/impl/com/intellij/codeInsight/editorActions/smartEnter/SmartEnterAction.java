package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.text.CharArrayUtil;

/**
 * @author max
 */
public class SmartEnterAction extends EditorAction {
  public SmartEnterAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, dataContext);
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Document doc = editor.getDocument();
      Project project = DataKeys.PROJECT.getData(dataContext);
      if (project == null || doc.getLineCount() < 2) {
        plainEnter(editor, dataContext);
        return;
      }
      final int caretOffset = editor.getCaretModel().getOffset();
      if (isInPreceedingBlanks(editor)) {
        final int caretLine = doc.getLineNumber(caretOffset);
        if (caretLine > 0) {
          int prevLineEnd = doc.getLineEndOffset(caretLine - 1);
          editor.getCaretModel().moveToOffset(prevLineEnd);
        }
        EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(
            IdeActions.ACTION_EDITOR_ENTER);
        enterHandler.execute(editor, dataContext);
        return;
      }

      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc);

      if (EnterHandler.isAfterUnmatchedLBrace(editor, caretOffset, psiFile.getFileType())) {
        EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(
            IdeActions.ACTION_EDITOR_ENTER);
        enterHandler.execute(editor, dataContext);
        return;
      }

      if (!(psiFile instanceof PsiJavaFile)) {
        plainEnter(editor, dataContext);
        return;
      }

      FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.complete.statement");

      final String textForRollback = doc.getText();
      try {
        new SmartEnterProcessor(project, editor, psiFile).process(0);
      }
      catch (SmartEnterProcessor.TooManyAttemptsException e) {
        doc.replaceString(0, doc.getTextLength(), textForRollback);
      }
    }

    private boolean isInPreceedingBlanks(Editor editor) {
      int offset = editor.getCaretModel().getOffset();
      final Document doc = editor.getDocument();
      CharSequence chars = doc.getCharsSequence();
      if (offset == doc.getTextLength() || chars.charAt(offset) == '\n') return false;

      int newLineOffset = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
      return newLineOffset < 0 || chars.charAt(newLineOffset) == '\n';
    }

    private void plainEnter(Editor editor, DataContext dataContext) {
      getEnterHandler().execute(editor, dataContext);
    }

    private EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
    }

    private PsiErrorElement getFirstUnresolvedError(PsiElement where) {
      final PsiErrorElement[] unresolvedError = new PsiErrorElement[]{null};
      traverseErrorElements(
          where, new ErrorElementProcessor() {
            public boolean process(PsiErrorElement errorElement) {
              unresolvedError[0] = errorElement;
              return false;
            }

            public boolean process(PsiLiteralExpression nonTerminatedString) {
              return true;
            }
          }
      );
      return unresolvedError[0];
    }

  }

  private interface ErrorElementProcessor {
    boolean process(PsiErrorElement errorElement);

    boolean process(PsiLiteralExpression nonTerminatedString);
  }

  private static void traverseErrorElements(PsiElement psiWhere, final ErrorElementProcessor processor) {
    psiWhere.accept(
        new PsiRecursiveElementVisitor() {
          boolean myIsStopped = false;

          public void visitElement(PsiElement element) {
            if (myIsStopped) return;
            super.visitElement(element);
          }

          public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitElement(expression);
          }

          public void visitErrorElement(PsiErrorElement element) {
            myIsStopped = !processor.process(element);
          }

          public void visitLiteralExpression(PsiLiteralExpression expression) {
            String parsingError = expression.getParsingError();
            if (parsingError != null && parsingError.indexOf(JavaErrorMessages.message("illegal.line.end.in.string.literal")) >= 0) {
              myIsStopped = !processor.process(expression);
            }
          }
        }
    );
  }
}


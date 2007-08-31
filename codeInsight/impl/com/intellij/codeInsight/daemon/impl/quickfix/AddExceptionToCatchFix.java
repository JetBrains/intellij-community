package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class AddExceptionToCatchFix extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    int offset = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element = findElement(file, offset);
    PsiTryStatement tryStatement = (PsiTryStatement) element.getParent();
    PsiClassType[] unhandledExceptions = ExceptionUtil.collectUnhandledExceptions(element, null);

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    PsiCodeBlock catchBlockToSelect = null;

    try {
      if (tryStatement.getFinallyBlock() == null && tryStatement.getCatchBlocks().length == 0) {
        for (PsiClassType unhandledException : unhandledExceptions) {
          addCatchStatement(tryStatement, unhandledException, file);
        }
        catchBlockToSelect = tryStatement.getCatchBlocks()[0];
      }
      else {
        for (int i = 0; i < unhandledExceptions.length; i++) {
          PsiClassType unhandledException = unhandledExceptions[i];
          PsiCodeBlock codeBlock = addCatchStatement(tryStatement, unhandledException, file);
          if (i == 0) catchBlockToSelect = codeBlock;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    if (catchBlockToSelect != null) {
      TextRange range = SurroundWithUtil.getRangeToSelect(catchBlockToSelect);
      editor.getCaretModel().moveToOffset(range.getStartOffset());

      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  private static PsiCodeBlock addCatchStatement(PsiTryStatement tryStatement, PsiClassType exceptionType, PsiFile file) throws IncorrectOperationException {
    PsiElementFactory factory = tryStatement.getManager().getElementFactory();

    CodeStyleManager styleManager = tryStatement.getManager().getCodeStyleManager();
    String name = styleManager.suggestVariableName(VariableKind.PARAMETER, null, null, exceptionType).names[0];
    name = styleManager.suggestUniqueVariableName(name, tryStatement, false);

    PsiCatchSection catchSection = factory.createCatchSection(exceptionType, name, file);

    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) {
      tryStatement.add(catchSection);
    }
    else {
      PsiElement finallyElement = finallyBlock;
      while (!(finallyElement instanceof PsiKeyword) && !finallyElement.getText().equals(PsiKeyword.FINALLY)) {
        finallyElement = finallyElement.getPrevSibling();
      }
      tryStatement.addBefore(catchSection, finallyElement);
    }

    PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
    parameters[parameters.length - 1].getTypeElement().replace(factory.createTypeElement(exceptionType));
    PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    PsiCodeBlock catchBlock = catchBlocks[catchBlocks.length - 1];

    return catchBlock;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = findElement(file, offset);

    if (element == null) return false;

    setText(QuickFixBundle.message("add.catch.clause.text"));
    return true;
  }

  private static PsiElement findElement(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) element = file.findElementAt(offset - 1);
    if (element == null) return null;
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, PsiMethod.class);
    if (parent == null || parent instanceof PsiMethod) return null;
    PsiTryStatement statement = (PsiTryStatement) parent;
    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock.getTextRange().getStartOffset() <= offset && tryBlock.getTextRange().getEndOffset() > offset) {
      if (ExceptionUtil.collectUnhandledExceptions(tryBlock, statement.getParent()).length != 0) {
        return tryBlock;
      }
    }
    return null;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.catch.clause.family");
  }
}

package com.intellij.codeInspection;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class SurroundWithIfFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.SurroundWithIfFix");
  private final PsiExpression myExpression;
  private final String myText;

  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.surround.if.quickfix", myExpression.getText());
  }

  public SurroundWithIfFix(PsiExpression expressionToAssert) {
    myExpression = expressionToAssert;
    myText = myExpression.getText();
  }

  private static Editor getEditor(Project project, PsiElement element) {
    FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(element.getContainingFile().getVirtualFile());
    for (FileEditor fileEditor : editors) {
      if (fileEditor instanceof TextEditor) return ((TextEditor)fileEditor).getEditor();
    }
    return null;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiStatement anchorStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    LOG.assertTrue(anchorStatement != null);
    Editor editor = getEditor(project, element);
    if (editor == null) return;
    PsiFile file = element.getContainingFile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    PsiElement[] elements = new PsiElement[]{anchorStatement};
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(anchorStatement, PsiWhiteSpace.class);
    if (prev instanceof PsiComment && SuppressManager.getInstance().getSuppressedInspectionIdsIn(prev) != null) {
      elements = new PsiElement[]{prev, anchorStatement};
    }
    try {
      TextRange textRange = new JavaWithIfSurrounder().surroundElements(project, editor, elements);
      if (textRange == null) return;

      @NonNls String newText = myText + " != null";
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),newText);
      editor.getCaretModel().moveToOffset(textRange.getEndOffset() + newText.length());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.surround.if.family");
  }
}

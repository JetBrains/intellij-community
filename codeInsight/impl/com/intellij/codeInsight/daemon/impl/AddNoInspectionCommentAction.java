package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class AddNoInspectionCommentAction implements IntentionAction {
  private final PsiElement myContext;
  private static final @NonNls String COMMENT_START_TEXT = "//noinspection ";

  private final String myID;

  public AddNoInspectionCommentAction(LocalInspectionTool tool, PsiElement context) {
    myID = tool.getID();
    myContext = context;
  }

  public AddNoInspectionCommentAction(HighlightDisplayKey key, PsiElement context) {
    myID = key.getID();
    myContext = context;
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.inspection.statement");
  }

  private PsiStatement getContainer() {
    return PsiTreeUtil.getParentOfType(myContext, PsiStatement.class);
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myContext.isValid() && myContext.getManager().isInProject(myContext) && getContainer() != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiStatement container = getContainer();
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(container.getContainingFile().getVirtualFile());
    if (status.hasReadonlyFiles()) return;
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(container, PsiWhiteSpace.class);
    PsiElementFactory factory = myContext.getManager().getElementFactory();
    if (prev instanceof PsiComment) {
      String text = prev.getText();
      if (text.startsWith(COMMENT_START_TEXT)) {
        prev.replace(factory.createCommentFromText(text + "," + myID, null));
        return;
      }
    }
    boolean caretWasBeforeStatement = editor.getCaretModel().getOffset() == container.getTextRange().getStartOffset();
    container.getParent().addBefore(factory.createCommentFromText(COMMENT_START_TEXT +  myID, null), container);
    if (caretWasBeforeStatement) {
      editor.getCaretModel().moveToOffset(container.getTextRange().getStartOffset());
    }
    UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
  }

  public boolean startInWriteAction() {
    return true;
  }
}

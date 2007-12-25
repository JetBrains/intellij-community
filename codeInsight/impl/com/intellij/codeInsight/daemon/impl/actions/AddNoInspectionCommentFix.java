package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspMethodCall;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class AddNoInspectionCommentFix extends SuppressIntentionAction {
  private static final @NonNls String COMMENT_START_TEXT = "//noinspection ";

  private final String myID;


  public AddNoInspectionCommentFix(HighlightDisplayKey key) {
    this(key.getID());
  }

  private AddNoInspectionCommentFix(final String ID) {
    myID = ID;
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.inspection.statement");
  }

  @Nullable
  private static PsiStatement getContainer(PsiElement context) {
    if (context == null || PsiTreeUtil.getParentOfType(context, JspMethodCall.class) != null) return null;
    return PsiTreeUtil.getParentOfType(context, PsiStatement.class);
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement context) {
    return context != null && context.getManager().isInProject(context) && getContainer(context) != null;
  }

  public void invoke(final Project project, @Nullable Editor editor, final PsiElement element) throws IncorrectOperationException {
    PsiStatement container = getContainer(element);
    if (container == null) return;

    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(container.getContainingFile().getVirtualFile());
    if (status.hasReadonlyFiles()) return;
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(container, PsiWhiteSpace.class);
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    if (prev instanceof PsiComment) {
      String text = prev.getText();
      if (text.startsWith(COMMENT_START_TEXT)) {
        prev.replace(factory.createCommentFromText(text + "," + myID, null));
        return;
      }
    }
    boolean caretWasBeforeStatement = editor != null && editor.getCaretModel().getOffset() == container.getTextRange().getStartOffset();
    container.getParent().addBefore(factory.createCommentFromText(COMMENT_START_TEXT +  myID, null), container);
    if (caretWasBeforeStatement) {
      editor.getCaretModel().moveToOffset(container.getTextRange().getStartOffset());
    }
    UndoUtil.markPsiFileForUndo(element.getContainingFile());
  }

  public boolean startInWriteAction() {
    return true;
  }
}

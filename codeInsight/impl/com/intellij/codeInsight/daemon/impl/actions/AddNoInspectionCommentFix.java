package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.command.undo.UndoManager;
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
public class AddNoInspectionCommentFix implements IntentionAction {
  private final SmartPsiElementPointer myContext;
  private static final @NonNls String COMMENT_START_TEXT = "//noinspection ";

  private final String myID;

  public AddNoInspectionCommentFix(LocalInspectionTool tool, PsiElement context) {
    this(tool.getID(), context);
  }

  public AddNoInspectionCommentFix(HighlightDisplayKey key, PsiElement context) {
    this(key.getID(), context);
  }

  private AddNoInspectionCommentFix(final String ID, final PsiElement context) {
    myID = ID;
    myContext = SmartPointerManager.getInstance(context.getProject()).createLazyPointer(context);
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.inspection.statement");
  }

  @Nullable
  private PsiStatement getContainer() {
    PsiElement context = myContext.getElement();
    if (context == null || PsiTreeUtil.getParentOfType(context, JspMethodCall.class) != null) return null;
    return PsiTreeUtil.getParentOfType(context, PsiStatement.class);
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiElement context = myContext.getElement();
    return context != null && context.getManager().isInProject(context) && getContainer() != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiStatement container = getContainer();
    if (container == null) return;

    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(container.getContainingFile().getVirtualFile());
    if (status.hasReadonlyFiles()) return;
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(container, PsiWhiteSpace.class);
    PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
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

package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiWhiteSpace;
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

  protected final String myID;
  protected Class<? extends PsiElement> mySuppressionHolderClass;

  public AddNoInspectionCommentFix(HighlightDisplayKey key, Class<? extends PsiElement> suppressionHolderClass) {
    this(key.getID());
    mySuppressionHolderClass = suppressionHolderClass;
  }

  private AddNoInspectionCommentFix(final String ID) {
    myID = ID;
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.inspection.statement");
  }

  @Nullable
  protected PsiElement getContainer(PsiElement context) {
    return PsiTreeUtil.getParentOfType(context, mySuppressionHolderClass);
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement context) {
    return context != null && context.getManager().isInProject(context) && getContainer(context) != null;
  }

  public void invoke(final Project project, @Nullable Editor editor, final PsiElement element) throws IncorrectOperationException {
    PsiElement container = getContainer(element);
    if (container == null) return;

    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(container.getContainingFile().getVirtualFile());
    if (status.hasReadonlyFiles()) return;
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(container, PsiWhiteSpace.class);
    if (prev instanceof PsiComment) {
      String text = prev.getText();
      if (text.startsWith(COMMENT_START_TEXT)) {
        replaceSuppressionComment(prev, text);
        return;
      }
    }
    boolean caretWasBeforeStatement = editor != null && editor.getCaretModel().getOffset() == container.getTextRange().getStartOffset();
    createSuppression(project, editor, element, container);

    if (caretWasBeforeStatement) {
      editor.getCaretModel().moveToOffset(container.getTextRange().getStartOffset());
    }
    UndoUtil.markPsiFileForUndo(element.getContainingFile());
  }

  private void replaceSuppressionComment(final PsiElement prev, final String text) throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(prev.getLanguage());
    assert commenter != null;
    final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(prev.getProject());
    final String prefix = commenter.getLineCommentPrefix();
    assert prefix != null && text.startsWith(prefix) : "Unexpected suppression comment " + text;
    String newText = text.substring(prefix.length()) + "," + myID;
    final PsiComment newComment = parserFacade.createLineCommentFromText((LanguageFileType)prev.getContainingFile().getFileType(), newText);
    prev.replace(newComment);
  }

  protected void createSuppression(final Project project,
                                 final Editor editor,
                                 final PsiElement element,
                                 final PsiElement container) throws IncorrectOperationException {
    final LanguageFileType fileType = (LanguageFileType) element.getContainingFile().getFileType();
    final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(project);
    final String text = SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
    PsiComment comment = parserFacade.createLineCommentFromText(fileType, text);
    container.getParent().addBefore(comment, container);
  }

  public boolean startInWriteAction() {
    return true;
  }
}

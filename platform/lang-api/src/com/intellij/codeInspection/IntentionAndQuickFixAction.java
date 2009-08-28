package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public abstract class IntentionAndQuickFixAction implements LocalQuickFix, IntentionAction{
  public static IntentionAndQuickFixAction[] EMPTY_ARRAY = new IntentionAndQuickFixAction[0];

  @NotNull
  public abstract String getName();

  @NotNull
  public abstract String getFamilyName();

  public abstract void applyFix(final Project project, final PsiFile file, @Nullable final Editor editor);

  @NotNull
  public final String getText() {
    return getName();
  }

  public final void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    applyFix(project, descriptor.getPsiElement().getContainingFile(), null);
  }

  public final void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    applyFix(project, file, editor);
  }

  /**
   *  In general case will be called if invoked as IntentionAction.
   */
  public boolean isAvailable(@NotNull final Project project, @Nullable final Editor editor, final PsiFile file) {
    return true;
  }

  public boolean startInWriteAction() {
    return true;
  }
}

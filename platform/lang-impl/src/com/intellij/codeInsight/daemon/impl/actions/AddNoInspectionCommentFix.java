package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class AddNoInspectionCommentFix extends AbstractAddNoInspectionCommentFix {
  protected Class<? extends PsiElement> mySuppressionHolderClass;

  public AddNoInspectionCommentFix(HighlightDisplayKey key, Class<? extends PsiElement> suppressionHolderClass) {
    this(key.getID());
    mySuppressionHolderClass = suppressionHolderClass;
  }

  private AddNoInspectionCommentFix(final String ID) {
    super(ID, false);
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.inspection.statement");
  }

  @Nullable
  protected PsiElement getContainer(PsiElement context) {
    return PsiTreeUtil.getParentOfType(context, mySuppressionHolderClass);
  }
}

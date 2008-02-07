package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: May 13, 2005
 */
public class AddSuppressInspectionForClassFix extends AddSuppressInspectionFix {

  public AddSuppressInspectionForClassFix(final HighlightDisplayKey key) {
    super(key);
  }

  public AddSuppressInspectionForClassFix(final String id) {
   super(id);
  }

  @Nullable protected PsiDocCommentOwner getContainer(final PsiElement element) {
    PsiDocCommentOwner container = super.getContainer(element);
    if (container == null || container instanceof PsiClass){
      return null;
    }
    while (container != null ) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass){
        return container;
      }
      container = parentClass;
    }
    return container;
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.inspection.class");
  }
}

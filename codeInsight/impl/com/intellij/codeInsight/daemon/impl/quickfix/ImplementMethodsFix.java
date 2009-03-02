package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplementMethodsFix extends IntentionAndQuickFixAction {
  private final PsiClass myClass;

  public ImplementMethodsFix(PsiClass aClass) {
    myClass = aClass;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myClass.isValid() && myClass.getManager().isInProject(myClass);
  }

  public void applyFix(final Project project, final PsiFile file, @Nullable final Editor editor) {
    if (editor == null || !CodeInsightUtilBase.prepareFileForWrite(myClass.getContainingFile())) return;
    OverrideImplementUtil.chooseAndImplementMethods(project, editor, myClass);
  }

  public boolean startInWriteAction() {
    return false;
  }

}

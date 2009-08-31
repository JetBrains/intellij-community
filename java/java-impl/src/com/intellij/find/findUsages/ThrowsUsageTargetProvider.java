/*
 * @author max
 */
package com.intellij.find.findUsages;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.Nullable;

public class ThrowsUsageTargetProvider implements UsageTargetProvider {
  @Nullable
  public UsageTarget[] getTargets(Editor editor, final PsiFile file) {
    if (editor == null || file == null) return null;

    PsiElement element = file.findElementAt(TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset()));
    if (element == null) return null;

    if (element instanceof PsiKeyword && PsiKeyword.THROWS.equals(element.getText())) {
      return new UsageTarget[]{new PsiElement2UsageTargetAdapter(element)};
    }

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiThrowStatement) {
      return new UsageTarget[] {new PsiElement2UsageTargetAdapter(parent)};
    }

    return null;
  }

  public UsageTarget[] getTargets(PsiElement psiElement) {
    return null;
  }
}
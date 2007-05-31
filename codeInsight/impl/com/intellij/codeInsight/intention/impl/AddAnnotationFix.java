package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class AddAnnotationFix implements IntentionAction {
  private final String myFQN;
  private final PsiModifierListOwner myModifierListOwner;

  public AddAnnotationFix(String fqn, PsiModifierListOwner modifierListOwner) {
    myFQN = fqn;
    myModifierListOwner = modifierListOwner;
  }

  @NotNull
  public String getText() {
    String name = myFQN.substring(myFQN.lastIndexOf('.') + 1);
    return CodeInsightBundle.message("intention.add.annotation.text", name);
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.annotation.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myModifierListOwner != null
      && myModifierListOwner.isValid()
      && PsiManager.getInstance(project).isInProject(myModifierListOwner)
      && myModifierListOwner.getModifierList() != null
      ;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiModifierList modifierList = myModifierListOwner.getModifierList();
    if (modifierList.findAnnotation(myFQN) != null) return;
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiManager manager = file.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    PsiAnnotation annotation = factory.createAnnotationFromText("@" + myFQN, myModifierListOwner);
    PsiElement inserted = modifierList.addAfter(annotation, null);
    CodeStyleManager.getInstance(project).shortenClassReferences(inserted);
  }

  public boolean startInWriteAction() {
    return true;
  }
}

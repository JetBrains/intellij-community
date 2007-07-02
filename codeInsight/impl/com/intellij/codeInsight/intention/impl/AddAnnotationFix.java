package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class AddAnnotationFix implements IntentionAction, LocalQuickFix {
  private final String myFQN;
  private final PsiModifierListOwner myModifierListOwner;
  private static final Logger LOG = Logger.getInstance("#" + AddAnnotationFix.class.getName());


  public AddAnnotationFix(String fqn, PsiModifierListOwner modifierListOwner) {
    myFQN = fqn;
    myModifierListOwner = modifierListOwner;
  }

  public AddAnnotationFix(final String FQN) {
    myFQN = FQN;
    myModifierListOwner = null;
  }

  @NotNull
  public String getText() {
    final String shortName = myFQN.substring(myFQN.lastIndexOf('.') + 1);
    if (myModifierListOwner instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)myModifierListOwner).getName();
      if (name != null) {
        FindUsagesProvider provider = myModifierListOwner.getLanguage().getFindUsagesProvider();
        return CodeInsightBundle.message("inspection.i18n.quickfix.annotate.element.as", provider.getType(myModifierListOwner), name, shortName);
      }
    }
    return CodeInsightBundle.message("inspection.i18n.quickfix.annotate.as", shortName);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.annotation.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      invoke(project, null, descriptor.getPsiElement().getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myModifierListOwner != null) {
      return myModifierListOwner.isValid()
             && PsiManager.getInstance(project).isInProject(myModifierListOwner)
             && myModifierListOwner.getModifierList() != null;
    }
    if (!file.getManager().isInProject(file)) {
      final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiMethod) {
          return true;
        }
        if (parent instanceof PsiField) {
          return true;
        }
        if (parent instanceof PsiParameter) {
          return true;
        }
      }
    }
    return false;
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance();
    if (myModifierListOwner != null) {
      final PsiModifierList modifierList = myModifierListOwner.getModifierList();
      LOG.assertTrue(modifierList != null);
      if (modifierList.findAnnotation(myFQN) != null) return;
      if (annotationsManager.useExternalAnnotations(myModifierListOwner)) {
        annotationsManager.annotateExternally(myModifierListOwner, myFQN);
      }
      else {
        if (!CodeInsightUtil.prepareFileForWrite(file)) return;
        PsiManager manager = file.getManager();
        PsiElementFactory factory = manager.getElementFactory();
        PsiAnnotation annotation = factory.createAnnotationFromText("@" + myFQN, myModifierListOwner);
        PsiElement inserted = modifierList.addAfter(annotation, null);
        CodeStyleManager.getInstance(project).shortenClassReferences(inserted);
      }
    }
    else {
      final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      annotationsManager.annotateExternally(PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class), myFQN);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}

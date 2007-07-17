package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return myModifierListOwner != null ?
           CodeInsightBundle.message("inspection.i18n.quickfix.annotate.as", shortName) :
           CodeInsightBundle.message("add.external.annotation.test", shortName);
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

  @Nullable
  protected static PsiModifierListOwner getContainer(final Editor editor, final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
    if (listOwner == null) {
      final PsiIdentifier psiIdentifier = PsiTreeUtil.getParentOfType(element, PsiIdentifier.class, false);
      if (psiIdentifier != null && psiIdentifier.getParent() instanceof PsiModifierListOwner) {
        listOwner = (PsiModifierListOwner)psiIdentifier.getParent();
      }
    }
    return listOwner;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(file)) > 0) return false;
    final PsiModifierListOwner owner;
    if (myModifierListOwner != null) {
      if (!myModifierListOwner.isValid() || !PsiManager.getInstance(project).isInProject(myModifierListOwner) ||
          myModifierListOwner.getModifierList() == null) {
        return false;
      }
      owner = myModifierListOwner;
    }
    else if (!file.getManager().isInProject(file)) {
      owner = getContainer(editor, file);
    }
    else {
      owner = null;
    }
    PsiType type = null;
    if (owner instanceof PsiMethod) {
      type = ((PsiMethod)owner).getReturnType();

    }
    else if (owner instanceof PsiVariable) {
      type = ((PsiVariable)owner).getType();
    }
    return owner != null && type instanceof PsiClassType && !AnnotationUtil.isAnnotated(owner, myFQN, false);
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    if (myModifierListOwner != null) {
      final PsiModifierList modifierList = myModifierListOwner.getModifierList();
      LOG.assertTrue(modifierList != null);
      if (modifierList.findAnnotation(myFQN) != null) return;
      if (annotationsManager.useExternalAnnotations(myModifierListOwner)) {
        annotationsManager.annotateExternally(myModifierListOwner, myFQN);
      }
      else {
        if (!CodeInsightUtil.preparePsiElementForWrite(modifierList)) return;
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

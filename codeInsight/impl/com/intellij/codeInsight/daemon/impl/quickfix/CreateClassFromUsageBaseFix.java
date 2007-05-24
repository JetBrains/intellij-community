package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public abstract class CreateClassFromUsageBaseFix extends BaseIntentionAction {
  protected static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromUsageBaseFix");
  protected CreateClassKind myKind;
  private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRefElement;

  public CreateClassFromUsageBaseFix(CreateClassKind kind, final PsiJavaCodeReferenceElement refElement) {
    myKind = kind;
    myRefElement = SmartPointerManager.getInstance(refElement.getProject()).createLazyPointer(refElement);
  }

  protected abstract String getText(String varName);

  private boolean isAvailableInContext(final @NotNull PsiJavaCodeReferenceElement element) {
    PsiElement parent = element.getParent();

    if (parent instanceof PsiTypeElement) {
      if (parent.getParent() instanceof PsiReferenceParameterList) return true;

      while (parent.getParent() instanceof PsiTypeElement) parent = parent.getParent();
      if (parent.getParent() instanceof PsiCodeFragment ||
          parent.getParent() instanceof PsiVariable ||
          parent.getParent() instanceof PsiMethod ||
          parent.getParent() instanceof PsiClassObjectAccessExpression ||
          parent.getParent() instanceof PsiTypeCastExpression ||
          (parent.getParent() instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent.getParent()).getCheckType() == parent)) {
        return true;
      }
    }
    else if (parent instanceof PsiReferenceList) {
      if (myKind == CreateClassKind.ENUM) return false;
      if (parent.getParent() instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)parent.getParent();
        if (psiClass.getExtendsList() == parent) {
          if (myKind == CreateClassKind.CLASS && !psiClass.isInterface()) return true;
          if (myKind == CreateClassKind.INTERFACE && psiClass.isInterface()) return true;
        }
        if (psiClass.getImplementsList() == parent && myKind == CreateClassKind.INTERFACE) return true;
      }
      else if (parent.getParent() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)parent.getParent();
        if (method.getThrowsList() == parent && myKind == CreateClassKind.CLASS) return true;
      }
    }
    else if (parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getBaseClassReference() == element) {
      return true;
    }

    if (element instanceof PsiReferenceExpression) {
      if (parent instanceof PsiMethodCallExpression) {
        return false;
      }
      return !(parent.getParent() instanceof PsiMethodCallExpression) || myKind == CreateClassKind.CLASS;
    }
    return false;
  }

  private static boolean checkClassName(String name) {
    return Character.isUpperCase(name.charAt(0));
  }

  public boolean isAvailable(final Project project, final Editor editor, final PsiFile file) {
    final PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null ||
        !element.getManager().isInProject(element) ||
        CreateFromUsageUtils.isValidReference(element, true)) return false;
    final String refName = element.getReferenceName();
    if (refName == null || !checkClassName(refName)) return false;
    PsiElement nameElement = element.getReferenceNameElement();
    if (nameElement == null) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression)) return false;
    if (!isAvailableInContext(element)) return false;
    final int offset = editor.getCaretModel().getOffset();
    if (CreateFromUsageUtils.shouldShowTag(offset, nameElement, element)) {
      setText(getText(nameElement.getText()));
      return true;
    }

    return false;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.class.from.usage.family");
  }

  @Nullable
  protected PsiJavaCodeReferenceElement getRefElement() {
    return myRefElement.getElement();
  }

  @Nullable
  protected static String getSuperClassName(final PsiJavaCodeReferenceElement element) {
    final String superClassName;
    if (element.getParent().getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element.getParent().getParent();
      if (method.getThrowsList() == element.getParent()) {
        superClassName = "java.lang.Exception";
      }
      else superClassName = null;
    }
    else superClassName = null;

    return superClassName;
  }
}

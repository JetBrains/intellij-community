package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import static com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class CreateClassFromUsageAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromUsageAction");
  private CreateClassKind myKind;

  private final SmartPsiElementPointer myRefElement;

  public CreateClassFromUsageAction(PsiJavaCodeReferenceElement refElement, CreateClassKind kind) {
    myKind = kind;
    myRefElement = SmartPointerManager.getInstance(refElement.getProject()).createLazyPointer(refElement);
  }

  public String getText(String varName) {
    return QuickFixBundle.message("create.class.from.usage.text", StringUtil.capitalize(myKind.getDescription()), varName);
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(getRefElement(), true)) {
      return;
    }
    final String superClassName;
    if (getRefElement().getParent().getParent()instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)getRefElement().getParent().getParent();
      if (method.getThrowsList() == getRefElement().getParent()) {
        superClassName = "java.lang.Exception";
      }
      else superClassName = null;
    }
    else superClassName = null;
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          final PsiClass aClass = CreateFromUsageUtils.createClass(getRefElement(), myKind, superClassName);
          if (aClass == null) return;
          try {
            getRefElement().bindToElement(aClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          OpenFileDescriptor descriptor = new OpenFileDescriptor(getRefElement().getProject(), aClass.getContainingFile().getVirtualFile(),
                                                                 aClass.getTextOffset());
          FileEditorManager.getInstance(aClass.getProject()).openTextEditor(descriptor, true);
        }
      }
    );
  }

  protected boolean isValidElement(PsiElement element) {
    return CreateFromUsageUtils.isValidReference((PsiReference)element, true);
  }

  protected PsiElement getElement() {
    final PsiJavaCodeReferenceElement element = getRefElement();
    if (!element.isValid() || !element.getManager().isInProject(element)) return null;
    if (!CreateFromUsageUtils.isValidReference(element, true) &&
        element.getReferenceNameElement() != null && checkClassName(element.getReferenceName())) {
      PsiElement parent = element.getParent();

      if (parent instanceof PsiTypeElement) {
        if (parent.getParent() instanceof PsiReferenceParameterList) return element;

        while (parent.getParent() instanceof PsiTypeElement) parent = parent.getParent();
        if (parent.getParent() instanceof PsiVariable || parent.getParent() instanceof PsiMethod ||
            parent.getParent() instanceof PsiClassObjectAccessExpression ||
            parent.getParent() instanceof PsiTypeCastExpression ||
            (parent.getParent() instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent.getParent()).getCheckType() == parent)) {
          return element;
        }
      }
      else if (parent instanceof PsiReferenceList) {
        if (myKind == ENUM) return null;
        if (parent.getParent() instanceof PsiClass) {
          PsiClass psiClass = (PsiClass)parent.getParent();
          if (psiClass.getExtendsList() == parent) {
            if (myKind == CLASS && !psiClass.isInterface()) return element;
            if (myKind == INTERFACE && psiClass.isInterface()) return element;
          }
          if (psiClass.getImplementsList() == parent && myKind == INTERFACE) return element;
        }
        else if (parent.getParent() instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)parent.getParent();
          if (method.getThrowsList() == parent && myKind == CLASS) return element;
        }
      }
      else if (parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getBaseClassReference() == element) {
        return element;
      }
    }

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;

      PsiElement parent = referenceExpression.getParent();

      if (parent instanceof PsiMethodCallExpression) {
        return null;
      }
      if (parent.getParent() instanceof PsiMethodCallExpression && myKind != CLASS) return null;

      if (referenceExpression.getReferenceNameElement() != null &&
          checkClassName(referenceExpression.getReferenceName()) &&
          !CreateFromUsageUtils.isValidReference(referenceExpression, true)) {
        return referenceExpression;
      }
    }

    return null;
  }

  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  private static boolean checkClassName(String name) {
    return Character.isUpperCase(name.charAt(0));
  }

  protected boolean isAvailableImpl(int offset) {
    PsiElement nameElement = getRefElement().getReferenceNameElement();
    if (nameElement == null) return false;
    PsiElement parent = getRefElement().getParent();
    if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression)) return false;
    if (shouldShowTag(offset, nameElement, getRefElement())) {
      setText(getText(nameElement.getText()));
      return true;
    }

    return false;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.class.from.usage.family");
  }

  public boolean startInWriteAction() {
    return false;
  }

  public PsiJavaCodeReferenceElement getRefElement() {
    return (PsiJavaCodeReferenceElement)myRefElement.getElement();
  }
}

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateClassFromUsageAction extends CreateFromUsageBaseAction {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromUsageAction");

  private final boolean myCreateInterface;
  private final SmartPsiElementPointer myRefElement;

  public CreateClassFromUsageAction(PsiJavaCodeReferenceElement refElement, boolean createInterface) {
    myRefElement = SmartPointerManager.getInstance(refElement.getProject()).createLazyPointer(refElement);
    myCreateInterface = createInterface;
  }

  public String getText(String varName) {
    if (myCreateInterface) {
      return QuickFixBundle.message("create.class.from.usage.interface.text", varName);
    }
    else {
      return QuickFixBundle.message("create.class.from.usage.class.text", varName);
    }
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
          final PsiClass aClass = CreateFromUsageUtils.createClass(getRefElement(), myCreateInterface, superClassName);
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
    if (!getRefElement().isValid() || !getRefElement().getManager().isInProject(getRefElement())) return null;
    if (!CreateFromUsageUtils.isValidReference(getRefElement(), true) &&
        getRefElement().getReferenceNameElement() != null && checkClassName(getRefElement().getReferenceName())) {
      PsiElement parent = getRefElement().getParent();

      if (parent instanceof PsiTypeElement) {
        if (parent.getParent() instanceof PsiReferenceParameterList) return getRefElement();

        while (parent.getParent() instanceof PsiTypeElement) parent = parent.getParent();
        if (parent.getParent() instanceof PsiVariable || parent.getParent() instanceof PsiMethod ||
            parent.getParent() instanceof PsiClassObjectAccessExpression ||
            parent.getParent() instanceof PsiTypeCastExpression ||
            (parent.getParent() instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent.getParent()).getCheckType() == parent)) {
          return getRefElement();
        }
      }
      else if (parent instanceof PsiReferenceList) {
        if (parent.getParent() instanceof PsiClass) {
          PsiClass psiClass = (PsiClass)parent.getParent();
          if (psiClass.getExtendsList() == parent) {
            if (!myCreateInterface && !psiClass.isInterface()) return getRefElement();
            if (myCreateInterface && psiClass.isInterface()) return getRefElement();
          }
          if (psiClass.getImplementsList() == parent && myCreateInterface) return getRefElement();
        }
        else if (parent.getParent() instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)parent.getParent();
          if (method.getThrowsList() == parent && !myCreateInterface) return getRefElement();
        }
      }
      else if (parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getBaseClassReference() == getRefElement()) {
        return getRefElement();
      }
    }

    if (getRefElement()instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)getRefElement();

      PsiElement parent = referenceExpression.getParent();

      if (parent instanceof PsiMethodCallExpression) {
        return null;
      }
      if (parent.getParent() instanceof PsiMethodCallExpression && myCreateInterface) return null;

      if (referenceExpression.getReferenceNameElement() != null &&
          checkClassName(referenceExpression.getReferenceName()) &&
          !CreateFromUsageUtils.isValidReference(referenceExpression, true)) {
        return referenceExpression;
      }
    }

    return null;
  }

  private boolean checkClassName(String name) {
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

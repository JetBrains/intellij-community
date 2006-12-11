package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import static com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind.*;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class CreateClassFromUsageAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromUsageAction");
  private CreateClassKind myKind;

  private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRefElement;

  public CreateClassFromUsageAction(PsiJavaCodeReferenceElement refElement, CreateClassKind kind) {
    myKind = kind;
    myRefElement = SmartPointerManager.getInstance(refElement.getProject()).createLazyPointer(refElement);
  }

  public String getText(String varName) {
    return QuickFixBundle.message("create.class.from.usage.text", StringUtil.capitalize(myKind.getDescription()), varName);
  }


  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    final PsiJavaCodeReferenceElement element = getRefElement();
    assert element != null;
    if (CreateFromUsageUtils.isValidReference(element, true)) {
      return;
    }
    final String superClassName;
    if (element.getParent().getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element.getParent().getParent();
      if (method.getThrowsList() == element.getParent()) {
        superClassName = "java.lang.Exception";
      }
      else superClassName = null;
    }
    else superClassName = null;
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          PsiJavaCodeReferenceElement refElement = element;
          final PsiClass aClass = CreateFromUsageUtils.createClass(refElement, myKind, superClassName);
          if (aClass == null) return;
          try {
            refElement = (PsiJavaCodeReferenceElement)refElement.bindToElement(aClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          OpenFileDescriptor descriptor = new OpenFileDescriptor(refElement.getProject(), aClass.getContainingFile().getVirtualFile(),
                                                                 aClass.getTextOffset());
          FileEditorManager.getInstance(aClass.getProject()).openTextEditor(descriptor, true);
        }
      }
    );
  }


  private boolean isAvailableInContext(final @NotNull PsiJavaCodeReferenceElement element) {
    PsiElement parent = element.getParent();

    if (parent instanceof PsiTypeElement) {
      if (parent.getParent() instanceof PsiReferenceParameterList) return true;

      while (parent.getParent() instanceof PsiTypeElement) parent = parent.getParent();
      if (parent.getParent() instanceof PsiVariable || parent.getParent() instanceof PsiMethod ||
          parent.getParent() instanceof PsiClassObjectAccessExpression ||
          parent.getParent() instanceof PsiTypeCastExpression ||
          (parent.getParent() instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent.getParent()).getCheckType() == parent)) {
        return true;
      }
    }
    else if (parent instanceof PsiReferenceList) {
      if (myKind == ENUM) return false;
      if (parent.getParent() instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)parent.getParent();
        if (psiClass.getExtendsList() == parent) {
          if (myKind == CLASS && !psiClass.isInterface()) return true;
          if (myKind == INTERFACE && psiClass.isInterface()) return true;
        }
        if (psiClass.getImplementsList() == parent && myKind == INTERFACE) return true;
      }
      else if (parent.getParent() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)parent.getParent();
        if (method.getThrowsList() == parent && myKind == CLASS) return true;
      }
    }
    else if (parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getBaseClassReference() == element) {
      return true;
    }

    if (element instanceof PsiReferenceExpression) {
      if (parent instanceof PsiMethodCallExpression) {
        return false;
      }
      if (parent.getParent() instanceof PsiMethodCallExpression && myKind != CLASS) return false;

      return true;
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

  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public PsiJavaCodeReferenceElement getRefElement() {
    return myRefElement.getElement();
  }
}

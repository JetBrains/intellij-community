package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.intention.impl.AddAnnotationAction;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.AnnotateMethodFix");
  private final String myAnnotation;

  public AnnotateMethodFix(final String fqn) {
    myAnnotation = fqn;
  }

  public String getName() {
    return MessageFormat.format(InspectionsBundle.message("inspection.annotate.quickfix.name"), ClassUtil.extractClassName(myAnnotation));
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method == null) return;
    PsiMethod superMethod = method.findDeepestSuperMethod();
    if (superMethod != null && !AnnotationUtil.isAnnotated(superMethod, myAnnotation, false)) {
      superMethod = SuperMethodWarningUtil.checkSuperMethod(method, InspectionsBundle.message("inspection.annotate.quickfix.verb"));
      if (superMethod != null && superMethod != method) {
        annotateMethod(superMethod);
      }
    }

    annotateMethod(method);
  }

  public String getFamilyName() {
    return getName();
  }

  private void annotateMethod(final PsiMethod method) {
    try {
      new AddAnnotationAction(myAnnotation, method).invoke(method.getProject(), null, method.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}

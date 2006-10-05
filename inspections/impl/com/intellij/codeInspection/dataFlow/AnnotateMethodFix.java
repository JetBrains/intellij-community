package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.impl.AddAnnotationAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.AnnotateMethodFix");
  private final String myAnnotation;

  public AnnotateMethodFix(final String fqn) {
    myAnnotation = fqn;
  }

  @NotNull
  public String getName() {
    return MessageFormat.format(InspectionsBundle.message("inspection.annotate.quickfix.name"), ClassUtil.extractClassName(myAnnotation));
  }

  public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method == null) return;
    List<PsiMethod> toAnnotate = new ArrayList<PsiMethod>();
    toAnnotate.add(method);
    List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (superMethod != null && !AnnotationUtil.isAnnotated(superMethod, myAnnotation, false) &&
          superMethod.getManager().isInProject(superMethod)) {
        int ret = askUserWhetherToAnnotateBaseMethod(method, superMethod, project);
        if (ret != 0 && ret != 1) return;
        if (ret == 0) {
          toAnnotate.add(superMethod);
        }
      }
    }
    for (PsiMethod psiMethod : toAnnotate) {
      annotateMethod(psiMethod);
    }
  }

  protected int askUserWhetherToAnnotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
    String implement = !method.hasModifierProperty(PsiModifier.ABSTRACT) && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)
                  ? InspectionsBundle.message("inspection.annotate.quickfix.implements")
                  : InspectionsBundle.message("inspection.annotate.quickfix.overrides");
    String message = InspectionsBundle.message("inspection.annotate.quickfix.overridden.method.messages",
                                               UsageViewUtil.getDescriptiveName(method), implement,
                                               UsageViewUtil.getDescriptiveName(superMethod));
    String title = InspectionsBundle.message("inspection.annotate.quickfix.overridden.method.warning");
    return Messages.showYesNoCancelDialog(project, message, title, Messages.getQuestionIcon());
  }

  @NotNull
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

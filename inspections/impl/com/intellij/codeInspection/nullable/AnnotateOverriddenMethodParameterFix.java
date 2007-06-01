package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.impl.AddAnnotationFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateOverriddenMethodParameterFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.AnnotateMethodFix");
  private final String myAnnotation;

  public AnnotateOverriddenMethodParameterFix(final String fqn) {
    myAnnotation = fqn;
  }

  @NotNull
  public String getName() {
    return InspectionsBundle.message("annotate.overridden.methods.parameters", ClassUtil.extractClassName(myAnnotation));
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiParameter parameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class, false);
    if (parameter == null) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int index = ArrayUtil.find(parameters, parameter);

    List<PsiParameter> toAnnotate = new ArrayList<PsiParameter>();

    PsiMethod[] methods = method.getManager().getSearchHelper().findOverridingMethods(method, GlobalSearchScope.allScope(project), true);
    for (PsiMethod psiMethod : methods) {
      PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
      if (index >= psiParameters.length) continue;
      PsiParameter psiParameter = psiParameters[index];
      if (!AnnotationUtil.isAnnotated(psiParameter, myAnnotation, false) && psiMethod.getManager().isInProject(psiMethod)) {
        toAnnotate.add(psiParameter);
      }
    }

    CodeInsightUtil.preparePsiElementsForWrite(toAnnotate);
    for (PsiParameter psiParam : toAnnotate) {
      try {
        new AddAnnotationFix(myAnnotation, psiParam).invoke(project, null, psiParam.getContainingFile());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }
}
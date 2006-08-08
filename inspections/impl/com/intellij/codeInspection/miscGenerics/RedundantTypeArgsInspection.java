package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class RedundantTypeArgsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection");

  public RedundantTypeArgsInspection() {
    myQuickFixAction = new MyQuickFixAction();
  }

  private LocalQuickFix myQuickFixAction;

  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.type.display.name");
  }

  public String getShortName() {
    return "RedundantTypeArguments";
  }



  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      return getDescriptions(body, manager);
    }
    return null;
  }

  public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager inspectionManager) {
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    place.accept(new PsiRecursiveElementVisitor() {
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          checkCallExpression(expression.getMethodExpression(), typeArguments, expression, inspectionManager, problems);
        }
      }

      public void visitNewExpression(PsiNewExpression expression) {
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            checkCallExpression(classReference, typeArguments, expression, inspectionManager, problems);
          }
        }
      }

      private void checkCallExpression(final PsiJavaCodeReferenceElement reference,
                                       final PsiType[] typeArguments,
                                       PsiCallExpression expression,
                                       final InspectionManager inspectionManager, final List<ProblemDescriptor> problems) {

        PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList == null) return;
        final JavaResolveResult resolveResult = reference.advancedResolve(false);

        final PsiElement element = resolveResult.getElement();
        if (element instanceof PsiMethod && resolveResult.isValidResult()) {
          PsiMethod method = (PsiMethod)element;
          final PsiTypeParameter[] typeParameters = method.getTypeParameters();
          if (typeParameters.length == typeArguments.length) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiResolveHelper resolveHelper = expression.getManager().getResolveHelper();
            for (int i = 0; i < typeParameters.length; i++) {
              PsiTypeParameter typeParameter = typeParameters[i];
              final PsiType inferedType = resolveHelper.inferTypeForMethodTypeParameter(typeParameter, parameters,
                                                                                        argumentList.getExpressions(),
                                                                                        resolveResult.getSubstitutor(), expression, false);
              if (!typeArguments[i].equals(inferedType)) return;
            }

            final PsiCallExpression copy = (PsiCallExpression)expression.copy(); //see IDEADEV-8174
            try {
              copy.getTypeArgumentList().delete();
              if (copy.resolveMethod() != element) return;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
              return;
            }

            final ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(expression.getTypeArgumentList(),
                                                                                           InspectionsBundle.message("inspection.redundant.type.problem.descriptor"),
                                                                                           myQuickFixAction,
                                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            problems.add(descriptor);
          }
        }
      }

    });

    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static class MyQuickFixAction implements LocalQuickFix {
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.type.remove.quickfix");
    }

    public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
      final PsiReferenceParameterList typeArgumentList = (PsiReferenceParameterList)descriptor.getPsiElement();
      try {
        typeArgumentList.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }
}

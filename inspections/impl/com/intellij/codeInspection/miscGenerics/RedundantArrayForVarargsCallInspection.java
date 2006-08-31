package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class RedundantArrayForVarargsCallInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection");
  private LocalQuickFix myQuickFixAction = new MyQuickFix();

  private static class MyQuickFix implements LocalQuickFix {
    public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
      PsiNewExpression arrayCreation = (PsiNewExpression) descriptor.getPsiElement();
      if (!CodeInsightUtil.prepareFileForWrite(arrayCreation.getContainingFile())) return;

      PsiExpressionList argumentList = (PsiExpressionList) arrayCreation.getParent();
      if (argumentList == null) return;
      PsiExpression[] args = argumentList.getExpressions();
      PsiArrayInitializerExpression arrayInitializer = arrayCreation.getArrayInitializer();
      try {
        if (arrayInitializer == null) {
          arrayCreation.delete();
          return;
        }
        
        PsiExpression[] initializers = arrayInitializer.getInitializers();
        if (initializers.length > 0) {
          argumentList.addRange(initializers[0], initializers[initializers.length - 1]);
        }
        args[args.length - 1].delete();
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.array.creation.quickfix");
    }
  }

  public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager manager) {
    if (PsiUtil.getLanguageLevel(place).compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    place.accept(new PsiRecursiveElementVisitor() {
      public void visitCallExpression(PsiCallExpression expression) {
        super.visitCallExpression(expression);
        checkCall(expression);
      }

      public void visitEnumConstant(PsiEnumConstant enumConstant) {
        super.visitEnumConstant(enumConstant);
        checkCall(enumConstant);
      }

      public void visitClass(PsiClass aClass) {
        //do not go inside to prevent multiple signals of the same problem
      }

      private void checkCall(PsiCall expression) {
        PsiMethod method = expression.resolveMethod();
        if (method != null && method.isVarArgs()) {
          PsiParameter[] parameters = method.getParameterList().getParameters();
          PsiExpressionList argumentList = expression.getArgumentList();
          if (argumentList != null) {
            PsiExpression[] args = argumentList.getExpressions();
            if (parameters.length == args.length) {
              PsiExpression lastArg = args[args.length - 1];
              PsiParameter lastParameter = parameters[args.length - 1];
              PsiType lastParamType = lastParameter.getType();
              LOG.assertTrue(lastParamType instanceof PsiEllipsisType);
              if (lastArg instanceof PsiNewExpression && ((PsiEllipsisType) lastParamType).toArrayType().equals(lastArg.getType())) {
                PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression) lastArg).getArrayInitializer();
                PsiExpression[] initializers = arrayInitializer != null ? arrayInitializer.getInitializers() : PsiExpression.EMPTY_ARRAY;
                if (isSafeToFlatten(expression, method, initializers)) {
                  final ProblemDescriptor descriptor = manager.createProblemDescriptor(lastArg,
                                                                                       InspectionsBundle.message("inspection.redundant.array.creation.for.varargs.call.descriptor"),
                                                                                       myQuickFixAction,
                                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

                  problems.add(descriptor);
                }
              }
            }
          }
        }
      }

      private boolean isSafeToFlatten(PsiCall callExpression,
                                      PsiMethod oldRefMethod,
                                      PsiExpression[] arrayElements) {
        PsiCall copy = (PsiCall) callExpression.copy();
        PsiExpressionList copyArgumentList = copy.getArgumentList();
        LOG.assertTrue(copyArgumentList != null);
        PsiExpression[] args = copyArgumentList.getExpressions();
        try {
          args[args.length - 1].delete();
          if (arrayElements.length > 0) {
            copyArgumentList.addRange(arrayElements[0], arrayElements[arrayElements.length - 1]);
          }
          return copy.resolveMethod() == oldRefMethod;
        } catch (IncorrectOperationException e) {
          return false;
        }
      }
    });
    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.array.creation.display.name");
  }

  @NonNls
  public String getShortName() {
    return "RedundantArrayCreation";
  }
}


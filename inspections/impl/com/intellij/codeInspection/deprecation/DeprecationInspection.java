package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class DeprecationInspection extends LocalInspectionTool {
  public static final String SHORT_NAME = "Deprecation";

  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
    if (!method.isConstructor()) {
      List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
      final ProblemDescriptor problemDescriptor = checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, manager);
      if (problemDescriptor != null){
        return new ProblemDescriptor[]{problemDescriptor};
      }
    }
    return null;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    final Set<ProblemDescriptor> problems = new HashSet<ProblemDescriptor>();
    file.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        JavaResolveResult result = reference.advancedResolve(true);
        PsiElement resolved = result.getElement();
        final ProblemDescriptor problemDescriptor = checkDeprecated(resolved, reference.getReferenceNameElement(), manager);
        if (problemDescriptor != null){
          problems.add(problemDescriptor);
        }
      }

      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);
        PsiType type = expression.getType();
        PsiExpressionList list = expression.getArgumentList();
        if (!(type instanceof PsiClassType)) return;
        PsiClassType.ClassResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
        PsiClass aClass = typeResult.getElement();
        if (aClass == null) return;
        if (aClass instanceof PsiAnonymousClass) {
          type = ((PsiAnonymousClass)aClass).getBaseClassType();
          typeResult = ((PsiClassType)type).resolveGenerics();
          aClass = typeResult.getElement();
          if (aClass == null) return;
        }
        final PsiResolveHelper resolveHelper = expression.getManager().getResolveHelper();
        final PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length > 0) {
          JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, list);
          MethodCandidateInfo result = null;
          if (results.length == 1) result = (MethodCandidateInfo)results[0];

          PsiMethod constructor = result == null ? null : result.getElement();
          if (constructor != null && expression.getClassReference() != null) {
            final ProblemDescriptor problemDescriptor = checkDeprecated(constructor, expression.getClassReference(), manager);
            if (problemDescriptor != null){
              problems.add(problemDescriptor);
            }
          }
        }
      }

      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        super.visitMethodCallExpression(methodCall);
        PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
        JavaResolveResult resolveResult = referenceToMethod.advancedResolve(true);
        PsiElement element = resolveResult.getElement();
        final ProblemDescriptor problemDescriptor = checkDeprecated(element, referenceToMethod.getReferenceNameElement(), manager);
        if (problemDescriptor != null){
          problems.add(problemDescriptor);
        }
      }
    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.deprecated.display.name");
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  //@top
  static ProblemDescriptor checkMethodOverridesDeprecated(MethodSignatureBackedByPsiMethod methodSignature,
                                                          List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                          InspectionManager manager) {
    PsiMethod method = methodSignature.getMethod();
    PsiElement methodName = method.getNameIdentifier();
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiClass aClass = superMethod.getContainingClass();
      if (aClass == null) continue;
      // do not show deprecated warning for class implementing deprecated methods
      if (!aClass.isDeprecated() && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (superMethod.isDeprecated()) {
        String description = JavaErrorMessages.message("overrides.deprecated.method",
                                                       HighlightMessageUtil.getSymbolName(aClass, PsiSubstitutor.EMPTY));
        return manager.createProblemDescriptor(methodName, description, (LocalQuickFix [])null, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
    return null;
  }

  @Nullable
  public static ProblemDescriptor checkDeprecated(PsiElement refElement,
                                                  PsiElement elementToHighlight,
                                                  InspectionManager manager) {
    if (!(refElement instanceof PsiDocCommentOwner)) return null;
    if (!((PsiDocCommentOwner)refElement).isDeprecated()) return null;

    String description = JavaErrorMessages.message("deprecated.symbol",
                                                   HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY));

    return manager.createProblemDescriptor(elementToHighlight, description, (LocalQuickFix[])null, ProblemHighlightType.LIKE_DEPRECATED);
  }
}

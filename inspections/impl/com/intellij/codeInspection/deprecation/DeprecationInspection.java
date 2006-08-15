package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */
public class DeprecationInspection extends LocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "Deprecation";
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.deprecated.display.name");


  @Nullable
  public PsiElementVisitor buildVisitor(final ProblemsHolder holder, boolean isOnTheFly) {
    return new DeprecationElementVisitor(holder);
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  @NonNls
  public String getID() {
    return "deprecation";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  private static class DeprecationElementVisitor extends PsiElementVisitor {
    private final ProblemsHolder myHolder;

    public DeprecationElementVisitor(final ProblemsHolder holder) {
      myHolder = holder;
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        JavaResolveResult result = reference.advancedResolve(true);
        PsiElement resolved = result.getElement();
        checkDeprecated(resolved, reference.getReferenceNameElement(), myHolder);
      }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void visitNewExpression(PsiNewExpression expression) {
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
      if (constructors.length > 0 && list != null) {
        JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, list);
        MethodCandidateInfo result = null;
        if (results.length == 1) result = (MethodCandidateInfo)results[0];

        PsiMethod constructor = result == null ? null : result.getElement();
        if (constructor != null && expression.getClassReference() != null) {
          checkDeprecated(constructor, expression.getClassReference(), myHolder);
        }
      }
    }

    public void visitMethod(PsiMethod method){
        MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
        if (!method.isConstructor()) {
          List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
          checkMethodOverridesDeprecated(methodSignature, superMethodSignatures, myHolder);
        }
      }
  }

  //@top
  static void checkMethodOverridesDeprecated(MethodSignatureBackedByPsiMethod methodSignature,
                                                          List<MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                          ProblemsHolder holder) {
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
        holder.registerProblem(methodName, description, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
  }

  static void checkDeprecated(PsiElement refElement,
                              PsiElement elementToHighlight,
                              ProblemsHolder holder) {
    if (!(refElement instanceof PsiDocCommentOwner)) return;
    if (!((PsiDocCommentOwner)refElement).isDeprecated()) return;

    String description = JavaErrorMessages.message("deprecated.symbol",
                                                   HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY));

    holder.registerProblem(elementToHighlight, description, ProblemHighlightType.LIKE_DEPRECATED);
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class RawUseOfParameterizedTypeInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreObjectConstruction = false;

  @SuppressWarnings("PublicField") public boolean ignoreTypeCasts = false;

  @SuppressWarnings("PublicField") public boolean ignoreUncompilable = true;

  @SuppressWarnings("PublicField") public boolean ignoreParametersOfOverridingMethods = false;

  @SuppressWarnings("PublicField") public boolean ignoreWhenQuickFixNotAvailable = false;

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "rawtypes";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "RawUseOfParameterized";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return JavaBundle.message("inspection.raw.use.of.parameterized.type.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreObjectConstruction", JavaBundle.message("raw.use.of.parameterized.type.ignore.new.objects.option")),
      checkbox("ignoreTypeCasts", JavaBundle.message("raw.use.of.parameterized.type.ignore.type.casts.option")),
      checkbox("ignoreUncompilable", JavaBundle.message("raw.use.of.parameterized.type.ignore.uncompilable.option")),
      checkbox("ignoreParametersOfOverridingMethods",
               JavaBundle.message("raw.use.of.parameterized.type.ignore.overridden.parameter.option")),
      checkbox("ignoreWhenQuickFixNotAvailable", JavaBundle.message("raw.use.of.parameterized.type.ignore.quickfix.not.available.option")));
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return (InspectionGadgetsFix)infos[0];
  }

  @Nullable
  private static InspectionGadgetsFix createFix(PsiElement target) {
    if (target instanceof PsiTypeElement && target.getParent() instanceof PsiVariable variable) {
      final PsiType type = getSuggestedType(variable);
      if (type != null) {
        final String typeText = GenericsUtil.getVariableTypeByExpressionType(type).getPresentableText();
        final String message =
          JavaBundle.message("raw.variable.type.can.be.generic.quickfix", variable.getName(), typeText);
        return new RawTypeCanBeGenericFix(message);
      }
    }
    if (target instanceof PsiJavaCodeReferenceElement ref) {
      PsiElement parent = target.getParent();
      if (parent instanceof PsiTypeElement typeElement) {
        PsiReferenceParameterList params = ref.getParameterList();
        // Can be erroneous empty <>
        if (params != null && !params.textMatches("")) return null;
        PsiTypeCastExpression cast = ObjectUtils.tryCast(typeElement.getParent(), PsiTypeCastExpression.class);
        if (cast == null) return null;
        if (!canUseUpperBound(cast)) return null;
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(cast.getType());
        if (psiClass == null) return null;
        int count = psiClass.getTypeParameters().length;
        return new CastQuickFix(typeElement.getText() + StreamEx.constant("?", count).joining(",", "<", ">"));
      }
      else if (parent instanceof PsiNewExpression newExpression) {
        if (!PsiUtil.isLanguageLevel7OrHigher(parent)) return null;
        if (newExpression.isArrayCreation() || newExpression.getAnonymousClass() != null) return null;
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(newExpression, false);
        if (expectedType == null || (expectedType instanceof PsiClassType && ((PsiClassType)expectedType).isRaw())) return null;
        PsiNewExpression copy = (PsiNewExpression)LambdaUtil.copyWithExpectedType(parent, expectedType);
        PsiJavaCodeReferenceElement reference = copy.getClassReference();
        if (reference == null) return null;
        PsiReferenceParameterList parameterList = reference.getParameterList();
        if (parameterList == null || !parameterList.textMatches("")) return null;
        parameterList.replace(PsiDiamondTypeUtil.createExplicitReplacement(parameterList));
        JavaResolveResult resolveResult = copy.resolveMethodGenerics();
        if (!resolveResult.isValidResult()) return null;
        PsiType diamondType = copy.getType();
        if (diamondType == null || !expectedType.isAssignableFrom(diamondType)) return null;
        return new UseDiamondFix();
      }
    }
    return null;
  }

  private static boolean canUseUpperBound(PsiTypeCastExpression cast) {
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(cast.getType());
    if (psiClass == null) return false;
    Set<PsiTypeParameter> parameters = Set.of(psiClass.getTypeParameters());
    if (parameters.isEmpty()) return false;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(cast.getParent());
    if (parent instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      if (!(target instanceof PsiMember)) return false;
      PsiClass containingClass = ((PsiMember)target).getContainingClass();
      if (containingClass == null) return false;
      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, PsiSubstitutor.EMPTY);
      if (target instanceof PsiField) {
        PsiType type = substitutor.substitute(((PsiField)target).getType());
        PsiClass varType = PsiUtil.resolveClassInClassTypeOnly(type);
        return (varType instanceof PsiTypeParameter && parameters.contains(varType) && !PsiUtil.isAccessedForWriting(ref)) ||
               !PsiTypesUtil.mentionsTypeParameters(type, parameters);
      }
      if (target instanceof PsiMethod) {
        PsiMethodCallExpression call = ObjectUtils.tryCast(ref.getParent(), PsiMethodCallExpression.class);
        if (call == null) return false;
        if (!ExpressionUtils.isVoidContext(call)) {
          PsiType type = substitutor.substitute(((PsiMethod)target).getReturnType());
          PsiClass varType = PsiUtil.resolveClassInClassTypeOnly(type);
          if (!(varType instanceof PsiTypeParameter && parameters.contains(varType)) &&
              PsiTypesUtil.mentionsTypeParameters(type, parameters)) {
            return false;
          }
        }
        PsiParameterList parameterList = ((PsiMethod)target).getParameterList();
        for (PsiParameter parameter : parameterList.getParameters()) {
          if (parameter.isVarArgs() && call.getArgumentList().getExpressionCount() == parameterList.getParametersCount() - 1) {
            // No vararg parameters specified
            continue;
          }
          PsiType parameterType = parameter.getType();
          if (PsiTypesUtil.mentionsTypeParameters(parameterType, Predicates.alwaysTrue())) return false;
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RawUseOfParameterizedTypeVisitor();
  }

  private class RawUseOfParameterizedTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (ignoreObjectConstruction) {
        return;
      }
      if (ignoreUncompilable && expression.isArrayCreation()) {
        //array creation can (almost) never be generic
        return;
      }
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      checkReferenceElement(classReference);
    }

    @Override
    public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
      PsiElement directParent = typeElement.getParent();
      if (directParent instanceof PsiVariable && getSuggestedType((PsiVariable)directParent) != null) {
        reportProblem(typeElement);
        return;
      }
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      super.visitTypeElement(typeElement);
      if (ignoreUncompilable && directParent instanceof PsiTypeElement) {
        PsiType parentType = ((PsiTypeElement)directParent).getType();
        if (parentType instanceof PsiArrayType) {
          if (PsiTreeUtil.skipParentsOfType(directParent, PsiTypeElement.class) instanceof PsiMethodReferenceExpression) {
            return;
          }
        }
      }
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(
        typeElement, PsiTypeElement.class, PsiReferenceParameterList.class, PsiJavaCodeReferenceElement.class);
      if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiClassObjectAccessExpression ||
          parent instanceof PsiDeconstructionPattern) {
        return;
      }
      if (ignoreTypeCasts && parent instanceof PsiTypeCastExpression) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(typeElement, PsiComment.class) != null) {
        return;
      }
      if (ignoreUncompilable && parent instanceof PsiAnnotationMethod) {
        // type of class type parameter cannot be parameterized if annotation method has default value
        final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)parent).getDefaultValue();
        if (defaultValue != null && directParent instanceof PsiTypeElement) {
          return;
        }
      }
      if (parent instanceof PsiParameter parameter) {
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod method) {
          if (ignoreParametersOfOverridingMethods) {
            if (MethodUtils.getSuper(method) != null || MethodCallUtils.isUsedAsSuperConstructorCallArgument(parameter, false)) {
              return;
            }
          }
          else if (ignoreUncompilable &&
                   (LibraryUtil.isOverrideOfLibraryMethod(method) || MethodCallUtils.isUsedAsSuperConstructorCallArgument(parameter, true))) {
            return;
          }
        }
      }
      final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
      checkReferenceElement(referenceElement);
    }

    private void reportProblem(@NotNull PsiElement element) {
      InspectionGadgetsFix fix = createFix(element);
      if (fix == null && ignoreWhenQuickFixNotAvailable) return;
      registerError(element, fix);
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement referenceParent = reference.getParent();
      if (!(referenceParent instanceof PsiReferenceList referenceList)) {
        return;
      }
      final PsiElement listParent = referenceList.getParent();
      if (!(listParent instanceof PsiClass)) {
        return;
      }
      if (referenceList.equals(((PsiClass)listParent).getPermitsList())) {
        return;
      }
      checkReferenceElement(reference);
    }

    private void checkReferenceElement(PsiJavaCodeReferenceElement reference) {
      if (reference == null) {
        return;
      }
      final PsiType[] typeParameters = reference.getTypeParameters();
      if (typeParameters.length > 0) {
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass aClass)) {
        return;
      }
      final PsiElement qualifier = reference.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement qualifierReference) {
        if (!aClass.hasModifierProperty(PsiModifier.STATIC) && !aClass.isInterface() && !aClass.isEnum()) {
          checkReferenceElement(qualifierReference);
        }
      }
      if (!aClass.hasTypeParameters()) {
        return;
      }
      reportProblem(reference);
    }
  }

  @Nullable
  private static PsiType getSuggestedType(@NotNull PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    final PsiType variableType = variable.getType();
    final PsiType initializerType = initializer.getType();
    if (!(variableType instanceof PsiClassType variableClassType)) return null;
    if (!variableClassType.isRaw()) return null;
    if (!(initializerType instanceof PsiClassType initializerClassType)) return null;
    if (initializerClassType.isRaw()) return null;
    final PsiClassType.ClassResolveResult variableResolveResult = variableClassType.resolveGenerics();
    final PsiClassType.ClassResolveResult initializerResolveResult = initializerClassType.resolveGenerics();
    if (initializerResolveResult.getElement() == null) return null;
    PsiClass variableResolved = variableResolveResult.getElement();
    if (variableResolved == null) return null;
    PsiSubstitutor targetSubstitutor = TypeConversionUtil
      .getClassSubstitutor(variableResolved, initializerResolveResult.getElement(), initializerResolveResult.getSubstitutor());
    if (targetSubstitutor == null) return null;
    PsiType type = JavaPsiFacade.getElementFactory(variable.getProject()).createType(variableResolved, targetSubstitutor);
    if (variableType.equals(type)) return null;
    return type;
  }

  private static class UseDiamondFix extends InspectionGadgetsFix implements HighPriorityAction {
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.insert.x", "<>");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiJavaCodeReferenceElement element = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiJavaCodeReferenceElement.class);
      if (element == null) return;
      PsiReferenceParameterList parameterList = element.getParameterList();
      if (parameterList == null) return;
      RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(parameterList);
    }
  }


  private static class CastQuickFix extends InspectionGadgetsFix {
    private final String myTargetType;

    private CastQuickFix(String type) {
      myTargetType = type;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("raw.variable.type.can.be.generic.cast.quickfix", myTargetType);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("raw.variable.type.can.be.generic.cast.quickfix.family");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiTypeElement cast = PsiTreeUtil.getNonStrictParentOfType(descriptor.getStartElement(), PsiTypeElement.class);
      if (cast == null) return;
      CodeStyleManager.getInstance(project).reformat(new CommentTracker().replace(cast, myTargetType));
    }
  }

  private static class RawTypeCanBeGenericFix extends InspectionGadgetsFix {
    private final @IntentionName String myName;

    RawTypeCanBeGenericFix(@NotNull @IntentionName String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("raw.variable.type.can.be.generic.family.quickfix");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getStartElement().getParent();
      if (element instanceof PsiVariable variable) {
        final PsiType type = getSuggestedType(variable);
        if (type != null) {
          var handler = CommonJavaRefactoringUtil.getRefactoringSupport().getChangeTypeSignatureHandler();
          handler.runHighlightingTypeMigrationSilently(project, null, PsiSearchHelper.getInstance(project).getUseScope(variable), variable, type);
        }
      }
    }
  }
}
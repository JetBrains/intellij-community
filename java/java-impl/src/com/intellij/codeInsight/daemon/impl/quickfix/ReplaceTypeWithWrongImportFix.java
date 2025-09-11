// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The `ReplaceTypeWithWrongImportFix` class provides a quick fix action for replacing
 * incorrect imported references in Java code with the correct corresponding class type.
 * Mostly, it supports variable and new expression types.
 */
public class ReplaceTypeWithWrongImportFix extends PsiUpdateModCommandAction<PsiJavaCodeReferenceElement> {

  @NotNull
  private final String myTargetClassName;

  private ReplaceTypeWithWrongImportFix(@NotNull PsiJavaCodeReferenceElement element,
                                        @NotNull String targetClass) {
    super(element);
    myTargetClassName = targetClass;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context,
                                                   @NotNull PsiJavaCodeReferenceElement reference) {
    PsiElement parent = reference.getParent();
    if (parent instanceof PsiNewExpression) {
      return Presentation.of(
          QuickFixBundle.message("replace.type.new.expression.name", myTargetClassName))
        .withPriority(PriorityAction.Priority.HIGH);
    }
    return Presentation.of(
        QuickFixBundle.message("replace.type.variable.name", myTargetClassName))
      .withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected @NotNull IntentionPreviewInfo generatePreview(ActionContext context, PsiJavaCodeReferenceElement originalElement) {

    ModCommand command = ModCommand.psiUpdate(originalElement, (e, upd) -> {
      PsiJavaCodeReferenceElement newReference =
        JavaPsiFacade.getElementFactory(context.project()).createReferenceFromText(myTargetClassName, e);
      e.replace(newReference);
    });

    return IntentionPreviewUtils.getModCommandPreview(command, context);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.type.new.expression.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context,
                        @NotNull PsiJavaCodeReferenceElement element,
                        @NotNull ModPsiUpdater updater) {
    PsiJavaCodeReferenceElement newReference =
      JavaPsiFacade.getElementFactory(context.project()).createReferenceFromText(myTargetClassName, element);
    PsiElement replaced = element.replace(newReference);
    JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(replaced.getParent());
  }

  /**
   * Creates a list of ReplaceTypeWithWrongImportFix instances for the given PsiJavaCodeReferenceElement.
   * @see ReplaceTypeWithWrongImportFix
   * @param ref the PsiJavaCodeReferenceElement to create fixes for
   * @return a list of ReplaceTypeWithWrongImportFix instances
   */
  @NotNull
  public static List<@NotNull ReplaceTypeWithWrongImportFix> createFixes(@NotNull PsiJavaCodeReferenceElement ref) {
    List<ReplaceTypeWithWrongImportFix> result = new ArrayList<>();
    if (ref.resolve() == null ||
        ref.isQualified()) {
      return result;
    }
    if (ref.getParent() instanceof PsiNewExpression newExpression) {
      String name = ref.getReferenceName();
      if (name == null) return Collections.emptyList();
      PsiType expectedType = ExpectedTypeUtils.findExpectedType(newExpression, false);
      if (expectedType == null) return Collections.emptyList();
      ReplaceTypeWithWrongImportFix fix = tryToCreateFix(ref, name, expectedType, false);
      if (fix != null) {
        result.add(fix);
      }
    }
    if (ref.getParent() instanceof PsiNewExpression newExpression &&
        newExpression.getParent() instanceof PsiVariable variable &&
        canProcessVariable(variable)) {
      result.addAll(processVariable(variable, result));
    }

    if (ref.getParent() instanceof PsiTypeElement typeElement &&
        typeElement.getParent() instanceof PsiVariable variable &&
        canProcessVariable(variable)) {
      result.addAll(processVariable(variable, result));
    }
    return result;
  }

  private static boolean canProcessVariable(@NotNull PsiVariable variable) {
    return variable.getTypeElement() != null &&
           !variable.getTypeElement().isInferredType() &&
           variable.getTypeElement().getInnermostComponentReferenceElement() != null;
  }

  private static @NotNull List<@NotNull ReplaceTypeWithWrongImportFix> processVariable(@NotNull PsiVariable variable,
                                                                                       @NotNull List<ReplaceTypeWithWrongImportFix> result) {
    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) return Collections.emptyList();
    PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
    if (referenceElement == null) return Collections.emptyList();
    String name = referenceElement.getReferenceName();
    if (name == null) return Collections.emptyList();
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null || initializer.getType() == null) return Collections.emptyList();
    ReplaceTypeWithWrongImportFix fix = tryToCreateFix(referenceElement, name, initializer.getType(), true);
    if (fix != null) {
      result.add(fix);
    }
    return result;
  }

  private static @Nullable ReplaceTypeWithWrongImportFix tryToCreateFix(@NotNull PsiJavaCodeReferenceElement ref,
                                                                        @NotNull String name,
                                                                        @NotNull PsiType expectedType,
                                                                        boolean leftActualAssignableType) {
    PsiFile psiFile = ref.getContainingFile();
    if (psiFile == null) return null;
    Project project = psiFile.getProject();
    GlobalSearchScope scope = psiFile.getResolveScope();
    if (!(expectedType instanceof PsiClassType classType)) {
      return null;
    }
    PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(classType);
    if (expectedClass == null) return null;
    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(name, scope);
    if (classes.length == 0) return null;
    if (classes.length > 4) return null;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    Condition<PsiClass> accessiblePredicate = aClass -> {
      if (facade.arePackagesTheSame(aClass, ref) ||
          PsiTreeUtil.getParentOfType(aClass, PsiImplicitClass.class) != null ||
          !PsiUtil.isAccessible(aClass, ref, null)) {
        return false;
      }
      if (leftActualAssignableType &&
          !InheritanceUtil.isInheritorOrSelf(expectedClass, aClass, true)) {
        return false;
      }
      if (!leftActualAssignableType &&
          !InheritanceUtil.isInheritorOrSelf(aClass, expectedClass, true)) {
        return false;
      }
      return true;
    };
    List<PsiClass> filtered = ContainerUtil.filter(classes, accessiblePredicate);
    if (filtered.size() != 1) {
      return null;
    }
    PsiClass aClass = filtered.getFirst();
    String targetClassType = aClass.getQualifiedName();
    if (targetClassType == null) {
      return null;
    }
    PsiReferenceParameterList refParameterList = ref.getParameterList();
    if (refParameterList == null) return null;
    if (refParameterList.getTypeArgumentCount() != 0 &&
        refParameterList.getTypeArgumentCount() == refParameterList.getTypeParameterElements().length &&
        !HighlightFixUtil.isPotentiallyCompatible(aClass, refParameterList)) {
      return null;
    }

    PsiSubstitutor aClassSubstitutor = inferSubstitutor(refParameterList, aClass);

    if (!leftActualAssignableType) {
      if (!(ref.getParent() instanceof PsiNewExpression newExpression)) return null;
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) return null;
      if (!newExpressionGenericCompatible(ref, aClass, argumentList, aClassSubstitutor)) return null;
      targetClassType += refParameterList.getText();
    }
    if (leftActualAssignableType) {
      GlobalSearchScope resolvedClassScope = aClass.getResolveScope();
      PsiSubstitutor substitutor = classType.resolveGenerics().getSubstitutor();
      PsiSubstitutor superClassSubstitutor =
        JavaClassSupers.getInstance().getSuperClassSubstitutor(aClass, expectedClass, resolvedClassScope, substitutor);
      if (superClassSubstitutor == null) {
        return null;
      }
      //can be broken, but let's keep the user's choice
      PsiSubstitutor combinedSubstitutor = superClassSubstitutor.putAll(aClassSubstitutor);
      PsiClassType superType = PsiElementFactory.getInstance(project).createType(aClass, combinedSubstitutor);
      targetClassType = superType.getCanonicalText();
    }

    return new ReplaceTypeWithWrongImportFix(ref, targetClassType);
  }

  private static @NotNull PsiSubstitutor inferSubstitutor(@NotNull PsiReferenceParameterList refParameterList,
                                                          @NotNull PsiClass aClass) {
    PsiSubstitutor aClassSubstitutor = PsiSubstitutor.EMPTY;
    PsiType @NotNull [] arguments = refParameterList.getTypeArguments();
    PsiTypeParameter[] parameters = aClass.getTypeParameters();
    for (int i = 0; i < Math.min(arguments.length, parameters.length); i++) {
      PsiType argument = arguments[i];
      PsiTypeParameter parameter = parameters[i];
      aClassSubstitutor = aClassSubstitutor.put(parameter, argument);
    }
    return aClassSubstitutor;
  }

  private static boolean newExpressionGenericCompatible(@NotNull PsiJavaCodeReferenceElement ref,
                                                        @NotNull PsiClass aClass,
                                                        @NotNull PsiExpressionList argumentList,
                                                        @NotNull PsiSubstitutor aClassSubstitutor) {
    boolean foundCompatibleConstructor = false;

    if (argumentList.isEmpty() && PsiUtil.hasDefaultConstructor(aClass)) {
      return true;
    }
    for (PsiMethod method : aClass.getMethods()) {
      if (!method.isConstructor()) continue;
      if (!PsiUtil.isAccessible(method, ref, null)) continue;
      PsiParameterList parameterList = method.getParameterList();
      PsiType[] argumentTypes = argumentList.getExpressionTypes();
      PsiParameter[] parameters = parameterList.getParameters();
      if (argumentTypes.length != parameters.length) continue;
      if (argumentList.getExpressionCount() != parameterList.getParametersCount()) continue;
      boolean parametersAssignable = true;
      for (int i = 0; i < argumentTypes.length; i++) {
        PsiType argumentType = argumentTypes[i];
        PsiParameter parameter = parameters[i];
        PsiType parameterType = parameter.getType();
        if (!TypeConversionUtil.isAssignable(aClassSubstitutor.substitute(parameterType), argumentType)) {
          parametersAssignable = false;
          break;
        }
      }
      if (!parametersAssignable) {
        continue;
      }
      foundCompatibleConstructor = true;
      break;
    }
    return foundCompatibleConstructor;
  }
}

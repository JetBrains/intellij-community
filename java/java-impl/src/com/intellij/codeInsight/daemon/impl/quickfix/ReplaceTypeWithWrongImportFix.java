// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.JavaClassSupers;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The `ReplaceTypeWithWrongImportFix` class provides a quick fix action for replacing
 * incorrect imported references in Java code with the correct corresponding class type.
 * Mostly, it supports variable and new expression types.
 */
public class ReplaceTypeWithWrongImportFix extends PsiUpdateModCommandAction<PsiJavaCodeReferenceElement> {

  private final @NotNull String myShortName;
  private final @NotNull String myExpectedType;
  private final boolean myLeftActualAssignableType;

  private ReplaceTypeWithWrongImportFix(@NotNull PsiJavaCodeReferenceElement element,
                                        @NotNull String shortName,
                                        @NotNull PsiType expectedType,
                                        boolean leftActualAssignableType) {
    super(element);
    myShortName = shortName;
    myExpectedType = expectedType.getCanonicalText();
    myLeftActualAssignableType = leftActualAssignableType;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context,
                                                   @NotNull PsiJavaCodeReferenceElement reference) {
    String myTargetClassName = getTargetClassName(reference);
    if (myTargetClassName == null) return null;
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

  @Nullable
  private String getTargetClassName(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement refOriginalElement = ref.getOriginalElement();
    return CachedValuesManager.getCachedValue(refOriginalElement, () -> {
      Project project = refOriginalElement.getProject();
      GlobalSearchScope scope = refOriginalElement.getResolveScope();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiType expectedType = factory.createTypeFromText(myExpectedType, ref);
      if (!(expectedType instanceof PsiClassType classType)) {
        return null;
      }
      PsiClass expectedClass = classType.resolve();
      if (expectedClass == null) return null;

      PsiReferenceParameterList refParameterList = ref.getParameterList();
      if (refParameterList == null) return null;

      Condition<PsiClass> accessiblePredicate = aClass -> {
        if (facade.arePackagesTheSame(aClass, ref) ||
            PsiTreeUtil.getParentOfType(aClass, PsiImplicitClass.class) != null ||
            !PsiUtil.isAccessible(aClass, ref, null)) {
          return false;
        }

        if (aClass.getQualifiedName() == null) {
          return false;
        }
        if (refParameterList.getTypeArgumentCount() != 0 &&
            refParameterList.getTypeArgumentCount() == refParameterList.getTypeParameterElements().length &&
            !HighlightFixUtil.isPotentiallyCompatible(aClass, refParameterList)) {
          return false;
        }

        if (myLeftActualAssignableType &&
            !InheritanceUtil.isInheritorOrSelf(expectedClass, aClass, true)) {
          return false;
        }
        if (!myLeftActualAssignableType &&
            !InheritanceUtil.isInheritorOrSelf(aClass, expectedClass, true)) {
          return false;
        }
        return true;
      };

      AtomicInteger counter = new AtomicInteger();
      List<PsiClass> filtered = new ArrayList<>();

      Processor<PsiClass> processor = psiClass -> {
        int count = counter.incrementAndGet();
        if (count > 5) {
          return false;
        }
        if (accessiblePredicate.test(psiClass)) {
          filtered.add(psiClass);
          return false;
        }
        return true;
      };

      PsiShortNamesCache.getInstance(project).processClassesWithName(myShortName, processor, scope, null);

      if (filtered.size() != 1) {
        return null;
      }
      PsiClass aClass = filtered.getFirst();
      String targetClassType = aClass.getQualifiedName();

      PsiSubstitutor aClassSubstitutor = inferSubstitutor(refParameterList, aClass);

      if (!myLeftActualAssignableType) {
        if (!(ref.getParent() instanceof PsiNewExpression newExpression)) return null;
        PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) return null;
        if (!newExpressionGenericCompatible(ref, aClass, argumentList, aClassSubstitutor)) return null;
        targetClassType += refParameterList.getText();
      }
      if (myLeftActualAssignableType) {
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
      return CachedValueProvider.Result.create(targetClassType, refOriginalElement);
    });
  }

  private static PsiElement replace(@NotNull PsiJavaCodeReferenceElement e,
                                    @NotNull String targetClassName) {
    PsiJavaCodeReferenceElement newReference =
      JavaPsiFacade.getElementFactory(e.getProject()).createReferenceFromText(targetClassName, e);
    return new CommentTracker().replaceAndRestoreComments(e, newReference);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.type.new.expression.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context,
                        @NotNull PsiJavaCodeReferenceElement element,
                        @NotNull ModPsiUpdater updater) {
    String targetClassName = getTargetClassName(element);
    if (targetClassName == null) {
      return;
    }
    PsiElement replaced = replace(element, targetClassName);
    if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
      JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(replaced.getParent());
    }
  }

  /**
   * Creates a list of ReplaceTypeWithWrongImportFix instances for the given PsiJavaCodeReferenceElement.
   *
   * @param ref the PsiJavaCodeReferenceElement to create fixes for
   * @return a list of ReplaceTypeWithWrongImportFix instances
   * @see ReplaceTypeWithWrongImportFix
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
    if (!(expectedType instanceof PsiClassType classType)) {
      return null;
    }
    PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(classType);
    if (expectedClass == null) return null;
    String expectedClassQualifiedName = expectedClass.getQualifiedName();
    if (expectedClassQualifiedName == null) return null;
    return new ReplaceTypeWithWrongImportFix(ref, name, expectedType, leftActualAssignableType);
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

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ReplaceConstructorWithFactoryAction implements ModCommandAction {
  @NotNull
  @Override
  public final String getFamilyName() {
    return JavaRefactoringBundle.message("replace.constructor.with.factory.method");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return getConstructorOrClass(context.findLeaf()) != null
           ? Presentation.of(getFamilyName()).withIcon(AllIcons.Actions.RefactoringBulb)
           : null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiElement element = context.findLeaf();
    PsiMember constructorOrClass = getConstructorOrClass(element);
    if (constructorOrClass == null) return ModCommand.nop();

    List<PsiClass> targets = StreamEx.iterate(constructorOrClass, Objects::nonNull, PsiMember::getContainingClass)
      .select(PsiClass.class).filter(cls -> cls.hasModifierProperty(PsiModifier.STATIC) || cls.getContainingClass() == null)
      .toList();
    SmartPsiElementPointer<PsiMember> constructorOrClassPtr = SmartPointerManager.createPointer(constructorOrClass);
    List<ModCommandAction> options =
      ContainerUtil.map(targets, target -> ModCommand.psiUpdateStep(
        target, PsiFormatUtil.formatClass(target, PsiFormatUtilBase.SHOW_NAME),
        (cls, updater) -> invoke(cls, updater, constructorOrClassPtr),
        cls -> Objects.requireNonNull(cls.getNameIdentifier()).getTextRange()));
    return new ModChooseAction(JavaBundle.message("popup.title.choose.target.class"), options);
  }

  private static void invoke(@NotNull PsiClass cls,
                             @NotNull ModPsiUpdater updater,
                             @NotNull SmartPsiElementPointer<PsiMember> constructorOrClassPtr) {
    PsiMember constructorOrClass = constructorOrClassPtr.getElement();
    if (constructorOrClass == null) return;
    UsageData usages = findUsages(constructorOrClass);
    performRefactoring(constructorOrClass, cls, usages, updater);
  }

  private static @PsiModifier.ModifierConstant String getMinimalAccessLevel(@NotNull PsiMember member,
                                                                            @NotNull List<@NotNull PsiElement> places) {
    String[] levels = {PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED};
    PsiClass containingClass = member.getContainingClass();
    for (String level : levels) {
      LightModifierList list = new LightModifierList(member.getManager(), JavaLanguage.INSTANCE, level);
      if (ContainerUtil.all(places, place -> JavaResolveUtil.isAccessible(member, containingClass, list, place, null, null))) {
        return level;
      }
    }
    return PsiModifier.PUBLIC;
  }

  private static void performRefactoring(@NotNull PsiMember constructorOrClass, @NotNull PsiClass targetClass,
                                         @NotNull UsageData usages, @NotNull ModPsiUpdater updater) {
    Project project = constructorOrClass.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiClass wrContainingClass = updater.getWritable(
      constructorOrClass instanceof PsiClass cls ? cls : Objects.requireNonNull(constructorOrClass.getContainingClass()));
    PsiReferenceExpression classReferenceExpression = factory.createReferenceExpression(targetClass);
    List<String> names = suggestNames(targetClass, Objects.requireNonNull(wrContainingClass.getName()));
    String factoryName = names.get(0);
    PsiReferenceExpression qualifiedMethodReference = (PsiReferenceExpression)factory.createExpressionFromText("A." + factoryName, null);
    PsiMethod constructor = ObjectUtils.tryCast(constructorOrClass, PsiMethod.class);

    PsiClass wrClass = updater.getWritable(targetClass);
    PsiMethod wrConstructor = updater.getWritable(constructor);
    List<PsiNewExpression> writableUsages = ContainerUtil.map(usages.newUsages, updater::getWritable);

    PsiMethod factoryMethod = createFactoryMethod(project, wrContainingClass, constructor, factoryName);
    factoryMethod = (PsiMethod)wrClass.add(factoryMethod);

    if (constructor == null) {
      wrConstructor = (PsiMethod)wrContainingClass.add(factory.createConstructor());
    }
    
    PsiUtil.setModifierProperty(wrConstructor, getMinimalAccessLevel(constructorOrClass, usages.otherUsages), true);

    for (PsiNewExpression newExpression : writableUsages) {
      var factoryCall = (PsiMethodCallExpression)factory.createExpressionFromText(factoryName + "()", newExpression);
      factoryCall.getArgumentList().replace(Objects.requireNonNull(newExpression.getArgumentList()));

      PsiExpression newQualifier = newExpression.getQualifier();

      PsiReferenceExpression factoryCallRef = factoryCall.getMethodExpression();
      PsiElement resolvedFactoryMethod = factoryCallRef.resolve();
      if (resolvedFactoryMethod != factoryMethod || newQualifier != null) {
        factoryCallRef = (PsiReferenceExpression)factoryCallRef.replace(qualifiedMethodReference);
        PsiElement qualifier = newQualifier == null ? classReferenceExpression : newQualifier;
        Objects.requireNonNull(factoryCallRef.getQualifierExpression()).replace(qualifier);
      }

      newExpression.replace(factoryCall);
    }
    updater.rename(factoryMethod, names);
  }

  private static List<String> suggestNames(@NotNull PsiClass psiClass, @NotNull String baseName) {
    int i = 0;
    String[] baseNames =
      {"create" + baseName, "new" + baseName, "get" + baseName, "createInstance", "getInstance", "newInstance", "create"};
    List<String> suggestions = new ArrayList<>();
    while (suggestions.size() < baseNames.length) {
      for (String name : baseNames) {
        String finalName = name + (i == 0 ? "" : i);
        if (psiClass.findMethodsByName(finalName, true).length == 0) {
          suggestions.add(finalName);
        }
      }
      i++;
    }
    return suggestions;
  }

  private static PsiMethod createFactoryMethod(@NotNull Project project,
                                               @NotNull PsiClass containingClass, @Nullable PsiMethod constructor,
                                               @NotNull String defaultFactoryName) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiClassType type = factory.createType(containingClass, PsiSubstitutor.EMPTY);
    PsiMethod factoryMethod = factory.createMethod(defaultFactoryName, type);
    if (constructor != null) {
      factoryMethod.getParameterList().replace(constructor.getParameterList());
      factoryMethod.getThrowsList().replace(constructor.getThrowsList());
    }

    Collection<String> names = new HashSet<>();
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(constructor != null ? constructor : containingClass)) {
      if (!names.contains(typeParameter.getName())) { //Otherwise type parameter is hidden in the constructor
        names.add(typeParameter.getName());
        Objects.requireNonNull(factoryMethod.getTypeParameterList()).addAfter(typeParameter, null);
      }
    }

    PsiReturnStatement returnStatement =
      (PsiReturnStatement)factory.createStatementFromText("return new A();", null);
    PsiNewExpression newExpression = (PsiNewExpression)Objects.requireNonNull(returnStatement.getReturnValue());
    PsiJavaCodeReferenceElement classRef = factory.createReferenceElementByType(type);
    Objects.requireNonNull(newExpression.getClassReference()).replace(classRef);
    PsiExpressionList argumentList = Objects.requireNonNull(newExpression.getArgumentList());

    PsiParameter[] params = factoryMethod.getParameterList().getParameters();

    for (PsiParameter parameter : params) {
      PsiExpression paramRef = factory.createExpressionFromText(parameter.getName(), null);
      argumentList.add(paramRef);
    }
    Objects.requireNonNull(factoryMethod.getBody()).add(returnStatement);

    PsiMember visibilityOwner = constructor == null ? containingClass : constructor;
    PsiUtil.setModifierProperty(factoryMethod, VisibilityUtil.getVisibilityModifier(visibilityOwner.getModifierList()), true);

    boolean inner = containingClass.getContainingClass() != null && !containingClass.hasModifierProperty(PsiModifier.STATIC);

    if (!inner) {
      PsiUtil.setModifierProperty(factoryMethod, PsiModifier.STATIC, true);
    }

    return (PsiMethod)CodeStyleManager.getInstance(project).reformat(factoryMethod);
  }


  record UsageData(@NotNull List<@NotNull PsiNewExpression> newUsages, @NotNull List<@NotNull PsiElement> otherUsages) {
  }

  private static @NotNull UsageData findUsages(@NotNull PsiMember constructorOrClass) {
    List<PsiNewExpression> newUsages = new ArrayList<>();
    List<PsiElement> otherUsages = new ArrayList<>();

    for (PsiReference reference : ReferencesSearch.search(constructorOrClass, constructorOrClass.getUseScope(), false)) {
      PsiElement element = reference.getElement();

      if (element.getParent() instanceof PsiNewExpression newExpression) {
        newUsages.add(newExpression);
      }
      else if ("super".equals(element.getText()) || "this".equals(element.getText())) {
        otherUsages.add(element);
      }
      else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
        otherUsages.add(element);
      }
      else if (element instanceof PsiClass) {
        otherUsages.add(element);
      }
    }
    return new UsageData(newUsages, otherUsages);
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("replace.constructor.with.factory.method.title");
  }

  @Nullable
  private static PsiMember getConstructorOrClass(@Nullable PsiElement element) {
    if (element == null) return null;
    PsiMethod method = MethodUtils.getJavaMethodFromHeader(element);
    if (method != null) {
      if (!method.isConstructor()) return null;
      var containingClass = method.getContainingClass();
      if (!isSuitableClass(containingClass)) return null;
      return method;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(element);
    if (!isSuitableClass(containingClass)) return null;
    PsiElement lBrace = containingClass.getLBrace();
    if (lBrace != null && element.getTextRange().getStartOffset() >= lBrace.getTextRange().getStartOffset()) return null;
    if (containingClass.getConstructors().length > 0) return null;
    return containingClass;
  }

  private static boolean isSuitableClass(PsiClass containingClass) {
    return containingClass != null && !containingClass.isInterface() && !containingClass.isEnum() && !containingClass.isRecord() &&
            !containingClass.hasModifierProperty(PsiModifier.ABSTRACT) && containingClass.getQualifiedName() != null;
  }
}

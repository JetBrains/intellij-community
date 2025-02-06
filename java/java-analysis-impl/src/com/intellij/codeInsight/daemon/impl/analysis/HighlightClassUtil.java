// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Checks and highlights problems with classes.
 * Generates HighlightInfoType.ERROR-only HighlightInfos at PsiClass level.
 */
public final class HighlightClassUtil {

  /**
   * @deprecated use {@link PsiTypesUtil#isRestrictedIdentifier(String, LanguageLevel)}
   */
  @Deprecated
  public static boolean isRestrictedIdentifier(@Nullable String typeName, @NotNull LanguageLevel level) {
    return PsiTypesUtil.isRestrictedIdentifier(typeName, level);
  }

  public static HighlightInfo.Builder checkExtendsSealedClass(@NotNull PsiClass aClass,
                                                       @NotNull PsiClass superClass,
                                                       @NotNull PsiJavaCodeReferenceElement elementToHighlight) {
    if (superClass.hasModifierProperty(PsiModifier.SEALED)) {
      if (PsiUtil.isLocalClass(aClass)) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(elementToHighlight)
          .descriptionAndTooltip(JavaErrorBundle.message("local.classes.must.not.extend.sealed.classes"));
        IntentionAction action = QuickFixFactory.getInstance().createConvertLocalToInnerAction(aClass);
        info.registerFix(action, null, null, null, null);
        return info;
      }

      if (!JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass) &&
          JavaModuleGraphUtil.findDescriptorByElement(aClass) == null) {
        String description = StringUtil.capitalize(JavaErrorBundle.message(
          "class.not.allowed.to.extend.sealed.class.from.another.package",
          JavaElementKind.fromElement(aClass).subject(), HighlightUtil.formatClass(aClass, false),
          JavaElementKind.fromElement(superClass).object(), HighlightUtil.formatClass(superClass, true)));
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description);
        PsiFile parentFile = superClass.getContainingFile();
        if (parentFile instanceof PsiClassOwner classOwner) {
          String parentPackage = classOwner.getPackageName();
          IntentionAction action = QuickFixFactory.getInstance().createMoveClassToPackageFix(aClass, parentPackage);
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }

      PsiClassType[] permittedTypes = superClass.getPermitsListTypes();
      if (permittedTypes.length > 0) {
        PsiManager manager = superClass.getManager();
        if (ContainerUtil.exists(permittedTypes, permittedType -> manager.areElementsEquivalent(aClass, permittedType.resolve()))) {
          return null;
        }
      }
      else if (aClass.getContainingFile() == superClass.getContainingFile()) {
        return null;
      }
      PsiIdentifier identifier = aClass.getNameIdentifier();
      if (identifier == null) {
        return null;
      }
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(JavaErrorBundle.message("not.allowed.in.sealed.hierarchy", aClass.getName()))
        .range(elementToHighlight);
      if (!(superClass instanceof PsiCompiledElement)) {
        IntentionAction action = QuickFixFactory.getInstance().createAddToPermitsListFix(aClass, superClass);
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }
    return null;
  }

  static void checkPermitsList(@NotNull PsiReferenceList list, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass aClass) || !list.equals(aClass.getPermitsList())) {
      return;
    }
    PsiJavaModule currentModule = JavaModuleGraphUtil.findDescriptorByElement(aClass);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(aClass.getProject());
    for (PsiJavaCodeReferenceElement permitted : list.getReferenceElements()) {

      for (PsiAnnotation annotation : PsiTreeUtil.findChildrenOfType(permitted, PsiAnnotation.class)) {
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotation)
          .descriptionAndTooltip(JavaErrorBundle.message("annotation.not.allowed.in.permit.list"));
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(annotation);
        builder.registerFix(action, null, null, null, null);
        errorSink.accept(builder);
      }

      PsiReferenceParameterList parameterList = permitted.getParameterList();
      if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList)
          .descriptionAndTooltip(JavaErrorBundle.message("permits.list.generics.are.not.allowed"));
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(parameterList);
        builder.registerFix(action, null, null, null, null);
        errorSink.accept(builder);
        continue;
      }
      @Nullable PsiElement resolve = permitted.resolve();
      if (resolve instanceof PsiClass inheritorClass) {
        PsiManager manager = inheritorClass.getManager();
        if (!ContainerUtil.exists(inheritorClass.getSuperTypes(), type -> manager.areElementsEquivalent(aClass, type.resolve()))) {
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(permitted)
            .descriptionAndTooltip(JavaErrorBundle.message("invalid.permits.clause.direct.implementation",
                                                           inheritorClass.getName(),
                                                           inheritorClass.isInterface() == aClass.isInterface() ? 1 : 2,
                                                           aClass.getName()));
          QuickFixAction.registerQuickFixActions(info, null,
                                                 QuickFixFactory.getInstance()
                                                   .createExtendSealedClassFixes(permitted, aClass, inheritorClass));
          errorSink.accept(info);
        }
        else {
          if (currentModule == null && !psiFacade.arePackagesTheSame(aClass, inheritorClass)) {
            String description = StringUtil.capitalize(
              JavaErrorBundle.message("class.not.allowed.to.extend.sealed.class.from.another.package",
                                      JavaElementKind.fromElement(inheritorClass).subject(), HighlightUtil.formatClass(inheritorClass, true),
                                      JavaElementKind.fromElement(aClass).object(), HighlightUtil.formatClass(aClass, false)));
            HighlightInfo.Builder info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(permitted).descriptionAndTooltip(description);
            PsiFile parentFile = aClass.getContainingFile();
            if (parentFile instanceof PsiClassOwner classOwner) {
              String parentPackage = classOwner.getPackageName();
              IntentionAction action = QuickFixFactory.getInstance().createMoveClassToPackageFix(inheritorClass, parentPackage);
              info.registerFix(action, null, null, null, null);
            }
            errorSink.accept(info);
          }
          else if (currentModule != null && !areModulesTheSame(currentModule, JavaModuleGraphUtil.findDescriptorByElement(inheritorClass))) {
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(permitted)
              .descriptionAndTooltip(JavaErrorBundle.message("class.not.allowed.to.extend.sealed.class.from.another.module"));
            errorSink.accept(info);
          }
          else if (!(inheritorClass instanceof PsiCompiledElement) && !hasPermittedSubclassModifier(inheritorClass)) {
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(permitted)
              .descriptionAndTooltip(JavaErrorBundle.message("permitted.subclass.must.have.modifier"));
            IntentionAction markNonSealed = QuickFixFactory.getInstance()
              .createModifierListFix(inheritorClass, PsiModifier.NON_SEALED, true, false);
            info.registerFix(markNonSealed, null, null, null, null);
            boolean hasInheritors = DirectClassInheritorsSearch.search(inheritorClass).findFirst() != null;
            if (!inheritorClass.isInterface() && !inheritorClass.hasModifierProperty(PsiModifier.ABSTRACT) || hasInheritors) {
              IntentionAction action = hasInheritors ?
                                       QuickFixFactory.getInstance().createSealClassFromPermitsListFix(inheritorClass) :
                                       QuickFixFactory.getInstance().createModifierListFix(inheritorClass, PsiModifier.FINAL, true, false);
              info.registerFix(action, null, null, null, null);
            }
            errorSink.accept(info);
          }
        }
      }
    }
  }

  private static boolean areModulesTheSame(@NotNull PsiJavaModule module, PsiJavaModule module1) {
    return module1 != null && module.getOriginalElement() == module1.getOriginalElement();
  }

  private static boolean hasPermittedSubclassModifier(@NotNull PsiClass psiClass) {
    PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers == null) return false;
    return modifiers.hasModifierProperty(PsiModifier.SEALED) ||
           modifiers.hasModifierProperty(PsiModifier.NON_SEALED) ||
           modifiers.hasModifierProperty(PsiModifier.FINAL);
  }
}

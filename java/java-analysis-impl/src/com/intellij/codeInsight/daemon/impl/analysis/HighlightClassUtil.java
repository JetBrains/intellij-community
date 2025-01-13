// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

  private static void registerMakeInnerClassStatic(@Nullable PsiClass aClass, @Nullable HighlightInfo.Builder result) {
    if (aClass != null && aClass.getContainingClass() != null) {
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.STATIC, true, false);
      if (result != null) {
        result.registerFix(action, null, null, null, null);
      }
    }
  }

  private static @NlsContexts.DetailedDescription String checkDefaultConstructorThrowsException(@NotNull PsiMethod constructor, PsiClassType @NotNull [] handledExceptions) {
    PsiClassType[] referencedTypes = constructor.getThrowsList().getReferencedTypes();
    List<PsiClassType> exceptions = new ArrayList<>();
    for (PsiClassType referencedType : referencedTypes) {
      if (!ExceptionUtil.isUncheckedException(referencedType) && !ExceptionUtil.isHandledBy(referencedType, handledExceptions)) {
        exceptions.add(referencedType);
      }
    }
    if (!exceptions.isEmpty()) {
      return HighlightUtil.getUnhandledExceptionsDescriptor(exceptions);
    }
    return null;
  }

  static HighlightInfo.Builder checkClassDoesNotCallSuperConstructorOrHandleExceptions(@NotNull PsiClass aClass,
                                                                                       @NotNull PsiResolveHelper resolveHelper) {
    if (aClass.isEnum()) return null;
    // check only no-ctr classes. Problem with specific constructor will be highlighted inside it
    if (aClass.getConstructors().length != 0) return null;
    // find no-args base class ctr
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkBaseClassDefaultConstructorProblem(aClass, resolveHelper, textRange, PsiClassType.EMPTY_ARRAY);
  }

  static HighlightInfo.Builder checkBaseClassDefaultConstructorProblem(@NotNull PsiClass aClass,
                                                                       @NotNull PsiResolveHelper resolveHelper,
                                                                       @NotNull TextRange range,
                                                                       PsiClassType @NotNull [] handledExceptions) {
    if (aClass instanceof PsiAnonymousClass) return null;
    PsiClass baseClass = aClass.getSuperClass();
    if (baseClass == null) return null;
    PsiMethod[] constructors = baseClass.getConstructors();
    if (constructors.length == 0) return null;

    PsiElement resolved = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(aClass, aClass.getProject(), baseClass);
    List<PsiMethod> constructorCandidates = (resolved != null ? Collections.singletonList((PsiMethod)resolved)
                                                              : Arrays.asList(constructors))
      .stream()
      .filter(constructor -> {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();
        return (parameters.length == 0 || parameters.length == 1 && parameters[0].isVarArgs()) &&
               resolveHelper.isAccessible(constructor, aClass, null);
      })
      .limit(2).toList();

    if (constructorCandidates.size() >= 2) {// two ambiguous var-args-only constructors
      String m1 = PsiFormatUtil.formatMethod(constructorCandidates.get(0), PsiSubstitutor.EMPTY,
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                             PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      String m2 = PsiFormatUtil.formatMethod(constructorCandidates.get(1), PsiSubstitutor.EMPTY,
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                             PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(range)
        .descriptionAndTooltip(JavaErrorBundle.message("ambiguous.method.call", m1, m2));

      IntentionAction action1 = QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass);
      info.registerFix(action1, null, null, null, null);
      IntentionAction action = QuickFixFactory.getInstance().createAddDefaultConstructorFix(baseClass);
      info.registerFix(action, null, null, null, null);
      return info;
    }

    if (!constructorCandidates.isEmpty()) {
      PsiMethod constructor = constructorCandidates.get(0);
      String description = checkDefaultConstructorThrowsException(constructor, handledExceptions);
      if (description != null) {
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description);
        IntentionAction action = QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass);
        info.registerFix(action, null, null, null, null);
        return info;
      }
      return null;
    }

    // no need to distract with missing constructor error when there is already a "Cannot inherit from final class" error message
    if (baseClass.hasModifierProperty(PsiModifier.FINAL)) return null;

    String description = JavaErrorBundle.message("no.default.constructor.available", HighlightUtil.formatClass(baseClass));
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description);
    IntentionAction action = QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass);
    info.registerFix(action, null, null, null, null);

    return info;
  }

  static HighlightInfo.Builder checkCreateInnerClassFromStaticContext(@NotNull PsiNewExpression expression, @NotNull PsiType type, @NotNull PsiClass aClass) {
    if (type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    if (aClass instanceof PsiAnonymousClass anonymousClass) {
      aClass = anonymousClass.getBaseClassType().resolve();
      if (aClass == null) return null;
    }

    PsiExpression qualifier = expression.getQualifier();
    return checkCreateInnerClassFromStaticContext(expression, qualifier, aClass);
  }

  public static HighlightInfo.Builder checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
                                                                     @Nullable PsiExpression qualifier,
                                                                     @NotNull PsiClass aClass) {
    PsiElement placeToSearchEnclosingFrom;
    if (qualifier != null) {
      PsiType qType = qualifier.getType();
      placeToSearchEnclosingFrom = PsiUtil.resolveClassInType(qType);
    }
    else {
      placeToSearchEnclosingFrom = element;
    }
    if (placeToSearchEnclosingFrom == null) {
      return null;
    }
    return checkCreateInnerClassFromStaticContext(element, placeToSearchEnclosingFrom, aClass);
  }

  static HighlightInfo.Builder checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
                                                              @NotNull PsiElement placeToSearchEnclosingFrom,
                                                              @NotNull PsiClass aClass) {
    if (!PsiUtil.isInnerClass(aClass)) return null;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) return null;

    if (outerClass instanceof PsiSyntheticClass ||
        InheritanceUtil.hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true, false)) {
      return null;
    }
    return checkIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, element);
  }

  static HighlightInfo.Builder checkSuperQualifierType(@NotNull Project project, @NotNull PsiMethodCallExpression superCall) {
    if (!JavaPsiConstructorUtil.isSuperConstructorCall(superCall)) return null;
    PsiMethod ctr = PsiTreeUtil.getParentOfType(superCall, PsiMethod.class, true, PsiMember.class);
    if (ctr == null) return null;
    PsiClass aClass = ctr.getContainingClass();
    if (aClass == null) return null;
    PsiClass targetClass = aClass.getSuperClass();
    if (targetClass == null) return null;
    PsiExpression qualifier = superCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null) {
      if (isRealInnerClass(targetClass)) {
        PsiClass outerClass = targetClass.getContainingClass();
        if (outerClass != null) {
          PsiClassType outerType = JavaPsiFacade.getElementFactory(project).createType(outerClass);
          return HighlightUtil.checkAssignability(outerType, null, qualifier, qualifier);
        }
      } else {
        String description = JavaErrorBundle.message("not.inner.class", HighlightUtil.formatClass(targetClass));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description);
      }
    }
    return null;
  }

  /** JLS 8.1.3. Inner Classes and Enclosing Instances */
  private static boolean isRealInnerClass(PsiClass aClass) {
    if (PsiUtil.isInnerClass(aClass)) return true;
    if (!PsiUtil.isLocalOrAnonymousClass(aClass)) return false;
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return false; // check for implicit staticness
    PsiMember member = PsiTreeUtil.getParentOfType(aClass, PsiMember.class, true);
    return member != null && !member.hasModifierProperty(PsiModifier.STATIC);
  }

  static HighlightInfo.Builder checkIllegalEnclosingUsage(@NotNull PsiElement place,
                                                  @Nullable PsiClass aClass,
                                                  @NotNull PsiClass outerClass,
                                                  @NotNull PsiElement elementToHighlight) {
    if (!PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      String description = JavaErrorBundle.message("is.not.an.enclosing.class", HighlightUtil.formatClass(outerClass));
      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description);
      registerMakeInnerClassStatic(aClass, highlightInfo);
      return highlightInfo;
    }
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
    if (staticParent != null) {
      String element = HighlightUtil.formatClass(outerClass) + "." +
                       (place instanceof PsiSuperExpression ? PsiKeyword.SUPER : PsiKeyword.THIS);
      String description = JavaErrorBundle.message("cannot.be.referenced.from.static.context", element);
      HighlightInfo.Builder builder =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description);
      // make context not static or referenced class static
      IntentionAction action1 = QuickFixFactory.getInstance().createModifierListFix(staticParent, PsiModifier.STATIC, false, false);
      builder.registerFix(action1, null, null, null, null);
      PsiModifierList classModifierList;
      if (aClass != null
          && (classModifierList = aClass.getModifierList()) != null
          && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, classModifierList) == null) {
        IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.STATIC, true, false);
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }
    return null;
  }

  static HighlightInfo.Builder checkExtendsSealedClass(@NotNull PsiFunctionalExpression expression, @NotNull PsiType functionalInterfaceType) {
    PsiClass functionalInterface = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceType);
    if (functionalInterface == null || !functionalInterface.hasModifierProperty(PsiModifier.SEALED)) return null;
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(expression)
      .descriptionAndTooltip(JavaErrorBundle.message("sealed.cannot.be.functional.interface"))
      ;
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

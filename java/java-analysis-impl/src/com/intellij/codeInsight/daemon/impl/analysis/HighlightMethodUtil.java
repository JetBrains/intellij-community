// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.codeInspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class HighlightMethodUtil {
  private static final Logger LOG = Logger.getInstance(HighlightMethodUtil.class);

  private static final MethodSignature ourValuesEnumSyntheticMethod =
    MethodSignatureUtil.createMethodSignature("values",
                                              PsiType.EMPTY_ARRAY,
                                              PsiTypeParameter.EMPTY_ARRAY,
                                              PsiSubstitutor.EMPTY);

  private HighlightMethodUtil() { }

  @NotNull
  static @NlsContexts.DetailedDescription String createClashMethodMessage(@NotNull PsiMethod method1, @NotNull PsiMethod method2, boolean showContainingClasses) {
    if (showContainingClasses) {
      PsiClass class1 = method1.getContainingClass();
      PsiClass class2 = method2.getContainingClass();
      if (class1 != null && class2 != null) {
        return JavaErrorBundle.message("clash.methods.message.show.classes",
                                       JavaHighlightUtil.formatMethod(method1),
                                       JavaHighlightUtil.formatMethod(method2),
                                       HighlightUtil.formatClass(class1),
                                       HighlightUtil.formatClass(class2));
      }
    }

    return JavaErrorBundle.message("clash.methods.message",
                                   JavaHighlightUtil.formatMethod(method1),
                                   JavaHighlightUtil.formatMethod(method2));
  }

  static HighlightInfo.Builder checkMethodWeakerPrivileges(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                           @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                           boolean includeRealPositionInfo,
                                                           @NotNull PsiFile containingFile, @Nullable Ref<? super String> description) {
    PsiMethod method = methodSignature.getMethod();
    PsiModifierList modifierList = method.getModifierList();
    if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) return null;
    int accessLevel = PsiUtil.getAccessLevel(modifierList);
    String accessModifier = PsiUtil.getAccessModifier(accessLevel);
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && !MethodSignatureUtil.isSuperMethod(superMethod, method)) continue;
      if (!PsiUtil.isAccessible(containingFile.getProject(), superMethod, method, null)) continue;
      if (!includeRealPositionInfo && MethodSignatureUtil.isSuperMethod(superMethod, method)) continue;
      HighlightInfo.Builder info = isWeaker(method, modifierList, accessModifier, accessLevel, superMethod, includeRealPositionInfo,
                                            description);
      if (info != null) return info;
    }
    return null;
  }

  private static HighlightInfo.Builder isWeaker(@NotNull PsiMethod method,
                                                @NotNull PsiModifierList modifierList,
                                                @NotNull String accessModifier,
                                                int accessLevel,
                                                @NotNull PsiMethod superMethod,
                                                boolean includeRealPositionInfo, @Nullable Ref<? super String> descriptionH) {
    int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
    if (accessLevel < superAccessLevel) {
      String description = JavaErrorBundle.message("weaker.privileges",
                                                   createClashMethodMessage(method, superMethod, true),
                                                   VisibilityUtil.toPresentableText(accessModifier),
                                                   PsiUtil.getAccessModifier(superAccessLevel));
      if (descriptionH != null) {
        descriptionH.set(description);
      }
      TextRange textRange = TextRange.EMPTY_RANGE;
      if (includeRealPositionInfo) {
        PsiElement keyword = PsiUtil.findModifierInList(modifierList, accessModifier);
        if (keyword != null) {
          textRange = keyword.getTextRange();
        }
        else {
          // in case of package-private or some crazy third-party plugin where some access modifier implied even if it's absent
          PsiIdentifier identifier = method.getNameIdentifier();
          if (identifier != null) {
            textRange = identifier.getTextRange();
          }
        }
      }
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
      IntentionAction action = QuickFixFactory.getInstance().createChangeModifierFix();
      info.registerFix(action, null, null, null, null);
      return info;
    }

    return null;
  }


  static HighlightInfo.Builder checkMethodIncompatibleReturnType(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                                 @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                                 boolean includeRealPositionInfo,
                                                                 @Nullable Ref<? super String> description) {
    return checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, includeRealPositionInfo, null, description);
  }

  static HighlightInfo.Builder checkMethodIncompatibleReturnType(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                                 @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                                 boolean includeRealPositionInfo,
                                                                 @Nullable TextRange textRange, @Nullable Ref<? super String> description) {
    PsiMethod method = methodSignature.getMethod();
    PsiType returnType = methodSignature.getSubstitutor().substitute(method.getReturnType());
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiType declaredReturnType = superMethod.getReturnType();
      PsiType superReturnType = declaredReturnType;
      if (superMethodSignature.isRaw()) superReturnType = TypeConversionUtil.erasure(declaredReturnType);
      if (returnType == null || superReturnType == null || method == superMethod) continue;
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      if (textRange == null && includeRealPositionInfo) {
        PsiTypeElement typeElement = method.getReturnTypeElement();
        if (typeElement != null) {
          textRange = typeElement.getTextRange();
        }
      }
      if (textRange == null) {
        textRange = TextRange.EMPTY_RANGE;
      }
      HighlightInfo.Builder info = checkSuperMethodSignature(
        superMethod, superMethodSignature, superReturnType, method, methodSignature, returnType,
        JavaErrorBundle.message("incompatible.return.type"), textRange, PsiUtil.getLanguageLevel(aClass), description);
      if (info != null) {
        return info;
      }
    }

    return null;
  }

  private static HighlightInfo.Builder checkSuperMethodSignature(@NotNull PsiMethod superMethod,
                                                                 @NotNull MethodSignatureBackedByPsiMethod superMethodSignature,
                                                                 @NotNull PsiType superReturnType,
                                                                 @NotNull PsiMethod method,
                                                                 @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                                 @NotNull PsiType returnType,
                                                                 @NotNull @Nls String detailMessage,
                                                                 @NotNull TextRange range,
                                                                 @NotNull LanguageLevel languageLevel,
                                                                 @Nullable Ref<? super String> description) {
    PsiClass superContainingClass = superMethod.getContainingClass();
    if (superContainingClass != null &&
        CommonClassNames.JAVA_LANG_OBJECT.equals(superContainingClass.getQualifiedName()) &&
        !superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && !superContainingClass.isInterface()) {
        return null;
      }
    }

    PsiType substitutedSuperReturnType;
    boolean hasGenerics = JavaFeature.GENERICS.isSufficient(languageLevel);
    if (hasGenerics && !superMethodSignature.isRaw() && superMethodSignature.equals(methodSignature)) { //see 8.4.5
      PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature,
                                                                                                  superMethodSignature);
      substitutedSuperReturnType = unifyingSubstitutor == null
                                   ? superReturnType
                                   : unifyingSubstitutor.substitute(superReturnType);
    }
    else {
      substitutedSuperReturnType = TypeConversionUtil.erasure(superMethodSignature.getSubstitutor().substitute(superReturnType));
    }

    if (returnType.equals(substitutedSuperReturnType)) return null;
    if (!(returnType instanceof PsiPrimitiveType) && substitutedSuperReturnType.getDeepComponentType() instanceof PsiClassType) {
      if (hasGenerics && LambdaUtil.performWithSubstitutedParameterBounds(methodSignature.getTypeParameters(),
                                                                      methodSignature.getSubstitutor(),
                                                                      () -> TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType))) {
        return null;
      }
    }

    return createIncompatibleReturnTypeMessage(method, superMethod, substitutedSuperReturnType, returnType, detailMessage, range,
                                               description);
  }

  @NotNull
  private static HighlightInfo.Builder createIncompatibleReturnTypeMessage(@NotNull PsiMethod method,
                                                             @NotNull PsiMethod superMethod,
                                                             @NotNull PsiType substitutedSuperReturnType,
                                                             @NotNull PsiType returnType,
                                                             @NotNull @Nls String detailMessage,
                                                             @NotNull TextRange textRange, @Nullable Ref<? super String> descriptionH) {
    String description = MessageFormat.format("{0}; {1}", createClashMethodMessage(method, superMethod, true), detailMessage);
    if (descriptionH != null) {
      descriptionH.set(description);
    }
    HighlightInfo.Builder errorResult =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
    if (method instanceof LightRecordMethod recordMethod) {
      for (IntentionAction fix :
        HighlightFixUtil.getChangeVariableTypeFixes(recordMethod.getRecordComponent(), substitutedSuperReturnType)) {
        errorResult.registerFix(fix, null, null, null, null);
      }
    }
    else {
      IntentionAction action = QuickFixFactory.getInstance().createMethodReturnFix(method, substitutedSuperReturnType, false);
      errorResult.registerFix(action, null, null, null, null);
    }
    IntentionAction action1 = QuickFixFactory.getInstance().createSuperMethodReturnFix(superMethod, returnType);
    errorResult.registerFix(action1, null, null, null, null);
    PsiClass returnClass = PsiUtil.resolveClassInClassTypeOnly(returnType);
    if (returnClass != null && substitutedSuperReturnType instanceof PsiClassType) {
      IntentionAction action =
        QuickFixFactory.getInstance().createChangeParameterClassFix(returnClass, (PsiClassType)substitutedSuperReturnType);
      errorResult.registerFix(action, null, null, null, null);
    }

    return errorResult;
  }


  static HighlightInfo.Builder checkMethodOverridesFinal(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                 @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures) {
    PsiMethod method = methodSignature.getMethod();
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      HighlightInfo.Builder info = checkSuperMethodIsFinal(method, superMethod);
      if (info != null) return info;
    }
    return null;
  }

  private static HighlightInfo.Builder checkSuperMethodIsFinal(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    // strange things happen when super method is from Object and method from interface
    if (superMethod.hasModifierProperty(PsiModifier.FINAL)) {
      PsiClass superClass = superMethod.getContainingClass();
      String description = JavaErrorBundle.message("final.method.override",
                                                   JavaHighlightUtil.formatMethod(method),
                                                   JavaHighlightUtil.formatMethod(superMethod),
                                                     superClass != null ? HighlightUtil.formatClass(superClass) : "<unknown>");
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
      QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(superMethod, MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false)));
      return errorResult;
    }
    return null;
  }

  static HighlightInfo.Builder checkMethodIncompatibleThrows(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                             @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                             boolean includeRealPositionInfo,
                                                             @NotNull PsiClass analyzedClass, @Nullable Ref<? super String> descriptionH) {
    PsiMethod method = methodSignature.getMethod();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, analyzedClass, PsiSubstitutor.EMPTY);
    PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
    PsiJavaCodeReferenceElement[] referenceElements;
    List<PsiElement> exceptionContexts;
    if (includeRealPositionInfo) {
      exceptionContexts = new ArrayList<>();
      referenceElements = method.getThrowsList().getReferenceElements();
    }
    else {
      exceptionContexts = null;
      referenceElements = null;
    }
    List<PsiClassType> checkedExceptions = new ArrayList<>();
    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exception = exceptions[i];
      if (exception == null) {
        LOG.error("throws: " + method.getThrowsList().getText() + "; method: " + method);
      }
      else if (!ExceptionUtil.isUncheckedException(exception)) {
        checkedExceptions.add(exception);
        if (includeRealPositionInfo && i < referenceElements.length) {
          PsiJavaCodeReferenceElement exceptionRef = referenceElements[i];
          exceptionContexts.add(exceptionRef);
        }
      }
    }
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      int index = getExtraExceptionNum(methodSignature, superMethodSignature, checkedExceptions, superSubstitutor);
      if (index != -1) {
        if (aClass.isInterface()) {
          PsiClass superContainingClass = superMethod.getContainingClass();
          if (superContainingClass != null && !superContainingClass.isInterface()) continue;
          if (superContainingClass != null && !aClass.isInheritor(superContainingClass, true)) continue;
        }
        PsiClassType exception = checkedExceptions.get(index);
        String description = JavaErrorBundle.message("overridden.method.does.not.throw",
                                                     createClashMethodMessage(method, superMethod, true),
                                                     JavaHighlightUtil.formatType(exception));
        if (descriptionH != null) {
          descriptionH.set(description);
        }
        TextRange textRange;
        if (includeRealPositionInfo) {
          PsiElement exceptionContext = exceptionContexts.get(index);
          textRange = exceptionContext.getTextRange();
        }
        else {
          textRange = TextRange.EMPTY_RANGE;
        }
        HighlightInfo.Builder errorResult =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
        IntentionAction action1 = QuickFixFactory.getInstance().createMethodThrowsFix(method, exception, false, false);
        errorResult.registerFix(action1, null, null, null, null);
        IntentionAction action = QuickFixFactory.getInstance().createMethodThrowsFix(superMethod, exception, true, true);
        errorResult.registerFix(action, null, null, null, null);
        return errorResult;
      }
    }
    return null;
  }

  // return number of exception  which was not declared in super method or -1
  private static int getExtraExceptionNum(@NotNull MethodSignature methodSignature,
                                          @NotNull MethodSignatureBackedByPsiMethod superSignature,
                                          @NotNull List<? extends PsiClassType> checkedExceptions,
                                          @NotNull PsiSubstitutor substitutorForDerivedClass) {
    PsiMethod superMethod = superSignature.getMethod();
    PsiSubstitutor substitutorForMethod = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superSignature);
    for (int i = 0; i < checkedExceptions.size(); i++) {
      PsiClassType checkedEx = checkedExceptions.get(i);
      PsiType substituted = substitutorForMethod == null ? TypeConversionUtil.erasure(checkedEx) : substitutorForMethod.substitute(checkedEx);
      PsiType exception = substitutorForDerivedClass.substitute(substituted);
      if (!isMethodThrows(superMethod, substitutorForMethod, exception, substitutorForDerivedClass)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isMethodThrows(@NotNull PsiMethod method,
                                        @Nullable PsiSubstitutor substitutorForMethod,
                                        @NotNull PsiType exception,
                                        @NotNull PsiSubstitutor substitutorForDerivedClass) {
    PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
    for (PsiClassType thrownException1 : thrownExceptions) {
      PsiType thrownException = substitutorForMethod != null ? substitutorForMethod.substitute(thrownException1) : TypeConversionUtil.erasure(thrownException1);
      thrownException = substitutorForDerivedClass.substitute(thrownException);
      if (TypeConversionUtil.isAssignable(thrownException, exception)) return true;
    }
    return false;
  }

  static void checkMethodCall(@NotNull PsiMethodCallExpression methodCall,
                              @NotNull PsiResolveHelper resolveHelper,
                              @NotNull LanguageLevel languageLevel,
                              @NotNull JavaSdkVersion javaSdkVersion,
                              @NotNull PsiFile file,
                              @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiExpressionList list = methodCall.getArgumentList();
    PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
    JavaResolveResult[] results = referenceToMethod.multiResolve(true);
    JavaResolveResult resolveResult = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    PsiElement resolved = resolveResult.getElement();

    boolean isDummy = isDummyConstructorCall(methodCall, resolveHelper, list, referenceToMethod);
    if (isDummy) return;
    HighlightInfo.Builder builder;

    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (resolved instanceof PsiMethod psiMethod && resolveResult.isValidResult()) {
      builder = HighlightUtil.checkUnhandledExceptions(methodCall);
      if (builder == null && psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          PsiElement element = ObjectUtils.notNull(referenceToMethod.getReferenceNameElement(), referenceToMethod);
          builder = HighlightUtil.checkFeature(element, JavaFeature.STATIC_INTERFACE_CALLS, languageLevel, file);
          if (builder == null) {
            builder = checkStaticInterfaceCallQualifier(referenceToMethod, resolveResult, methodCall, containingClass);
          }
        }
      }

      if (builder == null) {
        builder = GenericsHighlightUtil.checkInferredIntersections(substitutor, methodCall);
      }

      if (builder == null) {
        builder = checkVarargParameterErasureToBeAccessible((MethodCandidateInfo)resolveResult, methodCall);
      }

      if (builder == null) {
        builder = createIncompatibleTypeHighlightInfo(methodCall, resolveHelper, (MethodCandidateInfo)resolveResult, methodCall);
      }

      if (builder == null) {
        builder = checkInferredReturnTypeAccessible((MethodCandidateInfo)resolveResult, methodCall);
      }
    }
    else {
      MethodCandidateInfo candidateInfo = resolveResult instanceof MethodCandidateInfo ? (MethodCandidateInfo)resolveResult : null;
      PsiMethod resolvedMethod = candidateInfo != null ? candidateInfo.getElement() : null;

      if (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect()) {
        builder = null;
      }
      else if (candidateInfo != null && !candidateInfo.isApplicable()) {
        if (candidateInfo.isTypeArgumentsApplicable()) {
          builder = createIncompatibleCallHighlightInfo(list, candidateInfo, errorSink);
          if (builder != null) {
            PsiType expectedTypeByParent = InferenceSession.getTargetTypeByParent(methodCall);
            PsiType actualType = ((PsiExpression)methodCall.copy()).getType();
            TextRange fixRange = getFixRange(list);
            if (expectedTypeByParent != null && actualType != null && !expectedTypeByParent.isAssignableFrom(actualType)) {
              AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(builder, fixRange, methodCall, expectedTypeByParent, actualType);
            }
            HighlightFixUtil.registerQualifyMethodCallFix(resolveHelper.getReferencedMethodCandidates(methodCall, false), methodCall,
                                                          list, builder);
            registerMethodCallIntentions(builder, methodCall, list, resolveHelper);
            registerMethodReturnFixAction(builder, candidateInfo, methodCall);
            registerTargetTypeFixesBasedOnApplicabilityInference(methodCall, candidateInfo, resolvedMethod, builder);
            registerImplementsExtendsFix(builder, methodCall, resolvedMethod);
            errorSink.accept(builder);
            return;
          }
        }
        else {
          PsiReferenceParameterList typeArgumentList = methodCall.getTypeArgumentList();
          PsiSubstitutor applicabilitySubstitutor = candidateInfo.getSubstitutor(false);
          if (typeArgumentList.getTypeArguments().length == 0 && resolvedMethod.hasTypeParameters()) {
            builder = GenericsHighlightUtil.checkInferredTypeArguments(resolvedMethod, methodCall, applicabilitySubstitutor);
          }
          else {
            builder = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, applicabilitySubstitutor, javaSdkVersion);
          }
        }
      }
      else {
        String description = JavaErrorBundle.message("method.call.expected");
        builder =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(description);
        if (resolved instanceof PsiClass) {
          IntentionAction action = QuickFixFactory.getInstance().createInsertNewFix(methodCall, (PsiClass)resolved);
          builder.registerFix(action, null, null, null, null);
        }
        else {
          TextRange range = getFixRange(methodCall);
          registerStaticMethodQualifierFixes(methodCall, builder);
          registerUsageFixes(methodCall, builder, range);
          if (resolved instanceof PsiVariable variable && JavaFeature.LAMBDA_EXPRESSIONS.isSufficient(languageLevel)) {
            PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(variable.getType());
            if (method != null) {
              IntentionAction action = QuickFixFactory.getInstance().createInsertMethodCallFix(methodCall, method);
              builder.registerFix(action, null, null, range, null);
            }
          }
        }
      }
    }
    if (builder == null) {
      builder = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, substitutor, javaSdkVersion);
    }
    if (builder != null) {
      errorSink.accept(builder);
    }
  }

  private static void registerImplementsExtendsFix(@NotNull HighlightInfo.Builder builder, @NotNull PsiMethodCallExpression methodCall,
                                                   @NotNull PsiMethod resolvedMethod) {
    if (!JavaPsiConstructorUtil.isSuperConstructorCall(methodCall)) return;
    if (!resolvedMethod.isConstructor() || !resolvedMethod.getParameterList().isEmpty()) return;
    PsiClass psiClass = resolvedMethod.getContainingClass();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return;
    PsiClass containingClass = PsiUtil.getContainingClass(methodCall);
    if (containingClass == null) return;
    PsiReferenceList extendsList = containingClass.getExtendsList();
    if (extendsList != null && extendsList.getReferenceElements().length > 0) return;
    PsiReferenceList implementsList = containingClass.getImplementsList();
    if (implementsList == null) return;
    for (PsiClassType type : implementsList.getReferencedTypes()) {
      PsiClass superInterface = type.resolve();
      if (superInterface != null && !superInterface.isInterface()) {
        for (PsiMethod constructor : superInterface.getConstructors()) {
          if (!constructor.getParameterList().isEmpty()) {
            builder.registerFix(QuickFixFactory.getInstance().createChangeExtendsToImplementsFix(containingClass, type), null, null, null, null);
          }
        }
      }
    }
  }

  private static void registerStaticMethodQualifierFixes(@NotNull PsiMethodCallExpression methodCall, @NotNull HighlightInfo.Builder info) {
    TextRange methodExpressionRange = methodCall.getMethodExpression().getTextRange();
    info.registerFix(QuickFixFactory.getInstance().createStaticImportMethodFix(methodCall), null, null, methodExpressionRange, null);
    info.registerFix(QuickFixFactory.getInstance().createQualifyStaticMethodCallFix(methodCall), null, null, methodExpressionRange, null);
    info.registerFix(QuickFixFactory.getInstance().addMethodQualifierFix(methodCall), null, null, methodExpressionRange, null);
  }

  /**
   * collect highlightInfos per each wrong argument; fixes would be set for the first one with fixRange: methodCall
   * @return highlight info for the first wrong arg expression
   */
  private static HighlightInfo.Builder createIncompatibleCallHighlightInfo(@NotNull PsiExpressionList list,
                                                                           @NotNull MethodCandidateInfo candidateInfo,
                                                                           @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (PsiTreeUtil.hasErrorElements(list)) return null;
    PsiMethod resolvedMethod = candidateInfo.getElement();
    PsiSubstitutor substitutor = candidateInfo.getSubstitutor();
    String methodName = HighlightMessageUtil.getSymbolName(resolvedMethod, substitutor);
    PsiClass parent = resolvedMethod.getContainingClass();
    String containerName = parent == null ? "" : HighlightMessageUtil.getSymbolName(parent, substitutor);
    String argTypes = buildArgTypesList(list, false);
    String description = JavaErrorBundle.message("wrong.method.arguments", methodName, containerName, argTypes);
    String toolTip = null;
    List<PsiExpression> mismatchedExpressions;
    if (parent != null) {
      PsiExpression[] expressions = list.getExpressions();
      PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
      mismatchedExpressions = mismatchedArgs(expressions, substitutor, parameters, candidateInfo.isVarargs());
      if (mismatchedExpressions.size() == 1 && parameters.length > 0) {
        toolTip = createOneArgMismatchTooltip(candidateInfo, mismatchedExpressions, expressions, parameters);
      }
      if (toolTip == null) {
        if ((parameters.length == 0 || !parameters[parameters.length - 1].isVarArgs()) &&
            parameters.length != expressions.length) {
          toolTip = createMismatchedArgumentCountTooltip(parameters.length, expressions.length);
          description = JavaAnalysisBundle.message("arguments.count.mismatch", parameters.length, expressions.length);
        }
        else if (mismatchedExpressions.isEmpty()) {
          if (IncompleteModelUtil.isIncompleteModel(list)) return null;
          toolTip = XmlStringUtil.escapeString(description);
        }
        else {
          toolTip = createMismatchedArgumentsHtmlTooltip(candidateInfo, list);
        }
      }
    }
    else {
      mismatchedExpressions = Collections.emptyList();
      toolTip = XmlStringUtil.escapeString(description);
    }

    if (mismatchedExpressions.size() == list.getExpressions().length || mismatchedExpressions.isEmpty()) {
      if (list.getTextRange().isEmpty()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(ObjectUtils.notNull(list.getPrevSibling(), list))
          .description(description)
          .escapedToolTip(toolTip);
      }
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).description(description).escapedToolTip(toolTip).navigationShift(1);
    }
    else {
      HighlightInfo.Builder highlightInfo = null;
      for (PsiExpression wrongArg : mismatchedExpressions) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(wrongArg)
          .description(description)
          .escapedToolTip(toolTip);
        if (highlightInfo == null) {
          highlightInfo = info;
        }
        else {
          errorSink.accept(info);
        }
      }
      return highlightInfo;
    }
  }

  private static @NlsContexts.Tooltip String createOneArgMismatchTooltip(@NotNull MethodCandidateInfo candidateInfo,
                                                                         @NotNull List<? extends PsiExpression> mismatchedExpressions,
                                                                         PsiExpression @NotNull [] expressions,
                                                                         PsiParameter @NotNull [] parameters) {
    PsiExpression wrongArg = mismatchedExpressions.get(0);
    PsiType argType = wrongArg != null ? wrongArg.getType() : null;
    if (argType != null) {
      int idx = ArrayUtil.find(expressions, wrongArg);
      if (idx > parameters.length - 1 && !parameters[parameters.length - 1].isVarArgs()) return null;
      PsiType paramType = candidateInfo.getSubstitutor().substitute(PsiTypesUtil.getParameterType(parameters, idx, candidateInfo.isVarargs()));
      String errorMessage = candidateInfo.getInferenceErrorMessage();
      HtmlChunk reason = getTypeMismatchErrorHtml(errorMessage);
      return HighlightUtil.createIncompatibleTypesTooltip(
        paramType, argType, (lRawType, lTypeArguments, rRawType, rTypeArguments) ->
          JavaErrorBundle.message("incompatible.types.html.tooltip",
                                  lRawType, lTypeArguments, rRawType, rTypeArguments, reason, ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())));
    }
    return null;
  }

  static HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiCall methodCall,
                                                           @NotNull PsiResolveHelper resolveHelper,
                                                           @NotNull MethodCandidateInfo resolveResult,
                                                           @NotNull PsiElement elementToHighlight) {
    String errorMessage = resolveResult.getInferenceErrorMessage();
    if (errorMessage == null) return null;
    if (favorParentReport(methodCall, errorMessage)) return null;
    PsiMethod method = resolveResult.getElement();
    HighlightInfo.Builder builder;
    PsiType expectedTypeByParent = InferenceSession.getTargetTypeByParent(methodCall);
    PsiType actualType =
      methodCall instanceof PsiExpression ? ((PsiExpression)methodCall.copy()).getType() :
      resolveResult.getSubstitutor(false).substitute(method.getReturnType());
    TextRange fixRange = getFixRange(elementToHighlight);
    if (expectedTypeByParent != null && actualType != null && !expectedTypeByParent.isAssignableFrom(actualType)) {
      builder = HighlightUtil.createIncompatibleTypeHighlightInfo(
        expectedTypeByParent, actualType, fixRange, 0, XmlStringUtil.escapeString(errorMessage));
      if (methodCall instanceof PsiExpression) {
        AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(builder, fixRange, (PsiExpression)methodCall, expectedTypeByParent, actualType);
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
      if (parent instanceof PsiReturnStatement) {
        PsiParameterListOwner context = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
        if (context instanceof PsiMethod containingMethod) {
          HighlightUtil.registerReturnTypeFixes(builder, containingMethod, actualType);
        }
      } else if (parent instanceof PsiLocalVariable var) {
        HighlightFixUtil.registerChangeVariableTypeFixes(var, actualType, var.getInitializer(), builder);
      }
    }
    else {
      builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(errorMessage).range(fixRange);
    }
    if (methodCall instanceof PsiMethodCallExpression callExpression) {
      registerMethodCallIntentions(builder, callExpression, callExpression.getArgumentList(), resolveHelper);
      if (!PsiTypesUtil.mentionsTypeParameters(actualType, Set.of(method.getTypeParameters()))) {
        registerMethodReturnFixAction(builder, resolveResult, methodCall);
      }
      registerTargetTypeFixesBasedOnApplicabilityInference(callExpression, resolveResult, method, builder);
    }
    return builder;
  }

  private static boolean favorParentReport(@NotNull PsiCall methodCall, @NotNull String errorMessage) {
    if (errorMessage.equals(JavaPsiBundle.message("error.incompatible.type.failed.to.resolve.argument"))) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
      if (parent instanceof PsiExpressionList) {
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiCallExpression callExpression) {
          JavaResolveResult parentResolveResult = callExpression.resolveMethodGenerics();
          if (parentResolveResult instanceof MethodCandidateInfo info &&
              info.getInferenceErrorMessage() != null) {
            // Parent resolve failed as well, and it's likely more informative.
            // Suppress this error to allow reporting from parent
            return true;
          }
        }
      }
    }
    return false;
  }

  private static void registerUsageFixes(@NotNull PsiMethodCallExpression methodCall,
                                         @Nullable HighlightInfo.Builder highlightInfo,
                                         @NotNull TextRange range) {
    for (IntentionAction action : QuickFixFactory.getInstance().createCreateMethodFromUsageFixes(methodCall)) {
      if (highlightInfo != null) {
        highlightInfo.registerFix(action, null, null, range, null);
      }
    }
  }

  private static void registerThisSuperFixes(@NotNull PsiMethodCallExpression methodCall,
                                             @Nullable HighlightInfo.Builder highlightInfo,
                                             @NotNull TextRange range) {
    for (IntentionAction action : QuickFixFactory.getInstance().createCreateConstructorFromCallExpressionFixes(methodCall)) {
      if (highlightInfo != null) {
        highlightInfo.registerFix(action, null, null, range, null);
      }
    }
  }

  private static void registerTargetTypeFixesBasedOnApplicabilityInference(@NotNull PsiMethodCallExpression methodCall,
                                                                           @NotNull MethodCandidateInfo resolveResult,
                                                                           @NotNull PsiMethod resolved,
                                                                           HighlightInfo.Builder highlightInfo) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
    PsiVariable variable = null;
    if (parent instanceof PsiVariable) {
      variable = (PsiVariable)parent;
    }
    else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      PsiExpression lExpression = assignmentExpression.getLExpression();
      if (lExpression instanceof PsiReferenceExpression referenceExpression) {
        PsiElement resolve = referenceExpression.resolve();
        if (resolve instanceof PsiVariable) {
          variable = (PsiVariable)resolve;
        }
      }
    }

    if (variable != null) {
      PsiType rType = methodCall.getType();
      if (rType != null && !variable.getType().isAssignableFrom(rType)) {
        PsiType expectedTypeByApplicabilityConstraints = resolveResult.getSubstitutor(false).substitute(resolved.getReturnType());
        if (expectedTypeByApplicabilityConstraints != null && !variable.getType().isAssignableFrom(expectedTypeByApplicabilityConstraints) &&
            PsiTypesUtil.allTypeParametersResolved(variable, expectedTypeByApplicabilityConstraints)) {
          HighlightFixUtil.registerChangeVariableTypeFixes(variable, expectedTypeByApplicabilityConstraints, methodCall, highlightInfo);
        }
      }
    }
  }

  static HighlightInfo.Builder checkStaticInterfaceCallQualifier(@NotNull PsiJavaCodeReferenceElement referenceToMethod,
                                                         @NotNull JavaResolveResult resolveResult,
                                                         @NotNull PsiElement elementToHighlight,
                                                         @NotNull PsiClass containingClass) {
    String message = checkStaticInterfaceMethodCallQualifier(referenceToMethod, resolveResult.getCurrentFileResolveScope(), containingClass);
    if (message != null) {
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message)
        .range(getFixRange(elementToHighlight));
      if (referenceToMethod instanceof PsiReferenceExpression referenceExpression) {
        IntentionAction action =
          QuickFixFactory.getInstance().createAccessStaticViaInstanceFix(referenceExpression, resolveResult);
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }
    return null;
  }

  /* see also PsiReferenceExpressionImpl.hasValidQualifier(), StaticImportResolveProcessor.checkStaticInterfaceMethodCallQualifier() */
  private static @NlsContexts.DetailedDescription String checkStaticInterfaceMethodCallQualifier(@NotNull PsiJavaCodeReferenceElement ref,
                                                                                                 @Nullable PsiElement scope,
                                                                                                 @NotNull PsiClass containingClass) {
    @Nullable PsiElement qualifierExpression = ref.getQualifier();
    if (qualifierExpression == null && PsiTreeUtil.isAncestor(containingClass, ref, true)) {
      return null;
    }

    PsiElement resolve = null;
    if (qualifierExpression == null && scope instanceof PsiImportStaticStatement statement) {
      resolve = statement.resolveTargetClass();
    }
    else if (qualifierExpression instanceof PsiJavaCodeReferenceElement element) {
      resolve = element.resolve();
    }

    if (containingClass.getManager().areElementsEquivalent(resolve, containingClass)) {
      return null;
    }

    if (resolve instanceof PsiTypeParameter typeParameter) {
      Set<PsiClass> classes = new HashSet<>();
      for (PsiClassType type : typeParameter.getExtendsListTypes()) {
        PsiClass aClass = type.resolve();
        if (aClass != null) {
          classes.add(aClass);
        }
      }

      if (classes.size() == 1 && classes.contains(containingClass)) {
        return null;
      }
    }

    return JavaErrorBundle.message("static.interface.method.call.qualifier");
  }

  private static void registerMethodReturnFixAction(@NotNull HighlightInfo.Builder highlightInfo,
                                                    @NotNull MethodCandidateInfo candidate,
                                                    @NotNull PsiCall methodCall) {
    if (candidate.getInferenceErrorMessage() != null && methodCall.getParent() instanceof PsiReturnStatement) {
      PsiMethod containerMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiLambdaExpression.class);
      if (containerMethod != null) {
        PsiMethod method = candidate.getElement();
        PsiExpression methodCallCopy =
          JavaPsiFacade.getElementFactory(method.getProject()).createExpressionFromText(methodCall.getText(), methodCall);
        PsiType methodCallTypeByArgs = methodCallCopy.getType();
        //ensure type params are not included
        methodCallTypeByArgs = JavaPsiFacade.getElementFactory(method.getProject())
          .createRawSubstitutor(method).substitute(methodCallTypeByArgs);
        if (methodCallTypeByArgs != null) {
          @Nullable TextRange fixRange = getFixRange(methodCall);
          IntentionAction action = QuickFixFactory.getInstance().createMethodReturnFix(containerMethod, methodCallTypeByArgs, true);
          highlightInfo.registerFix(action, null, null, fixRange, null);
        }
      }
    }
  }

  @NotNull
  private static List<PsiExpression> mismatchedArgs(PsiExpression @NotNull [] expressions,
                                                    PsiSubstitutor substitutor,
                                                    PsiParameter @NotNull [] parameters,
                                                    boolean varargs) {
    List<PsiExpression> result = new ArrayList<>();
    for (int i = 0; i < Math.max(parameters.length, expressions.length); i++) {
      if (parameters.length == 0 || !assignmentCompatible(i, parameters, expressions, substitutor, varargs)) {
        result.add(i < expressions.length ? expressions[i] : null);
      }
    }

    return result;
  }

  static boolean isDummyConstructorCall(@NotNull PsiMethodCallExpression methodCall,
                                        @NotNull PsiResolveHelper resolveHelper,
                                        @NotNull PsiExpressionList list,
                                        @NotNull PsiReferenceExpression referenceToMethod) {
    boolean isDummy = false;
    boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
    if (isThisOrSuper) {
      // super(..) or this(..)
      if (list.isEmpty()) { // implicit ctr call
        CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
        if (candidates.length == 1 && !candidates[0].getElement().isPhysical()) {
          isDummy = true;// dummy constructor
        }
      }
    }
    return isDummy;
  }

  static HighlightInfo.Builder checkAmbiguousMethodCallIdentifier(@NotNull PsiReferenceExpression referenceToMethod,
                                                          JavaResolveResult @NotNull [] resolveResults,
                                                          @NotNull PsiExpressionList list,
                                                          @Nullable PsiElement element,
                                                          @NotNull JavaResolveResult resolveResult,
                                                          @NotNull PsiMethodCallExpression methodCall,
                                                          @NotNull PsiResolveHelper resolveHelper,
                                                          @NotNull LanguageLevel languageLevel,
                                                          @NotNull PsiFile file) {
    MethodCandidateInfo methodCandidate2 = findCandidates(resolveResults).second;
    if (methodCandidate2 != null) return null;
    MethodCandidateInfo[] candidates = toMethodCandidates(resolveResults);

    HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
    String description;
    PsiElement elementToHighlight = ObjectUtils.notNull(referenceToMethod.getReferenceNameElement(), referenceToMethod);
    if (element != null && !resolveResult.isAccessible()) {
      description = HighlightUtil.accessProblemDescription(referenceToMethod, element, resolveResult);
    }
    else if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      if (element instanceof PsiMethod psiMethod && psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          HighlightInfo.Builder info = HighlightUtil.checkFeature(elementToHighlight, JavaFeature.STATIC_INTERFACE_CALLS, languageLevel, file);
          if (info != null) return info;
          info = checkStaticInterfaceCallQualifier(referenceToMethod, resolveResult, elementToHighlight, containingClass);
          if (info != null) return info;
        }
      }

      description = HighlightUtil.staticContextProblemDescription(element);
    }
    else if (candidates.length == 0) {
      PsiClass qualifierClass = RefactoringChangeUtil.getQualifierClass(referenceToMethod);
      String className = qualifierClass != null ? qualifierClass.getName() : null;
      PsiExpression qualifierExpression = referenceToMethod.getQualifierExpression();

      if (className != null) {
        if (IncompleteModelUtil.isIncompleteModel(file) &&
            IncompleteModelUtil.canBePendingReference(referenceToMethod)) {
          return HighlightUtil.getPendingReferenceHighlightInfo(elementToHighlight);
        }
        description = JavaErrorBundle.message("ambiguous.method.call.no.match", referenceToMethod.getReferenceName(), className);
      }
      else if (qualifierExpression != null &&
               qualifierExpression.getType() instanceof PsiPrimitiveType primitiveType &&
               !primitiveType.equals(PsiTypes.nullType()) && !primitiveType.equals(PsiTypes.voidType())) {
        description =
          JavaErrorBundle.message("cannot.call.method.on.type", qualifierExpression.getText(), primitiveType.getPresentableText(false));
      }
      else {
        if (IncompleteModelUtil.isIncompleteModel(file) && IncompleteModelUtil.canBePendingReference(referenceToMethod)) {
          return HighlightUtil.getPendingReferenceHighlightInfo(elementToHighlight);
        }
        description =
          JavaErrorBundle.message("cannot.resolve.method", referenceToMethod.getReferenceName() + buildArgTypesList(list, true));
      }
      highlightInfoType = HighlightInfoType.WRONG_REF;
    }
    else {
      return null;
    }

    String toolTip = XmlStringUtil.escapeString(description);
    HighlightInfo.Builder builder =
      HighlightInfo.newHighlightInfo(highlightInfoType).range(elementToHighlight).description(description).escapedToolTip(toolTip);
    if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      HighlightFixUtil.registerStaticProblemQuickFixAction(builder, element, referenceToMethod);
    }
    registerMethodCallIntentions(builder, methodCall, list, resolveHelper);

    TextRange fixRange = getFixRange(elementToHighlight);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, builder, fixRange);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, methodCall, builder, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, builder, fixRange);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), builder, fixRange);
    PermuteArgumentsFix.registerFix(builder, methodCall, candidates, fixRange);
    var action = RemoveRepeatingCallFix.createFix(methodCall);
    if (action != null) {
      builder.registerFix(action, null, null, fixRange, null);
    }
    registerChangeParameterClassFix(methodCall, list, builder, fixRange);
    if (candidates.length == 0) {
      UnresolvedReferenceQuickFixUpdater.getInstance(file.getProject()).registerQuickFixesLater(methodCall.getMethodExpression(), builder);
    }
    return builder;
  }

  static HighlightInfo.Builder checkAmbiguousMethodCallArguments(@NotNull PsiReferenceExpression referenceToMethod,
                                                                 JavaResolveResult @NotNull [] resolveResults,
                                                                 @NotNull PsiExpressionList list,
                                                                 PsiElement element,
                                                                 @NotNull JavaResolveResult resolveResult,
                                                                 @NotNull PsiMethodCallExpression methodCall,
                                                                 @NotNull PsiResolveHelper resolveHelper,
                                                                 @NotNull PsiElement elementToHighlight) {
    Pair<MethodCandidateInfo, MethodCandidateInfo> pair = findCandidates(resolveResults);
    MethodCandidateInfo methodCandidate1 = pair.first;
    MethodCandidateInfo methodCandidate2 = pair.second;
    MethodCandidateInfo[] candidates = toMethodCandidates(resolveResults);

    String description;
    String toolTip;
    if (methodCandidate2 != null) {
      if (IncompleteModelUtil.isIncompleteModel(list) &&
          ContainerUtil.exists(list.getExpressions(), e -> IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(e))) {
        return null;
      }
      PsiMethod element1 = methodCandidate1.getElement();
      String m1 = PsiFormatUtil.formatMethod(element1,
                                             methodCandidate1.getSubstitutor(false),
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      PsiMethod element2 = methodCandidate2.getElement();
      String m2 = PsiFormatUtil.formatMethod(element2,
                                             methodCandidate2.getSubstitutor(false),
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      VirtualFile virtualFile1 = PsiUtilCore.getVirtualFile(element1);
      VirtualFile virtualFile2 = PsiUtilCore.getVirtualFile(element2);
      if (!Comparing.equal(virtualFile1, virtualFile2)) {
        if (virtualFile1 != null) m1 += " (In " + virtualFile1.getPresentableUrl() + ")";
        if (virtualFile2 != null) m2 += " (In " + virtualFile2.getPresentableUrl() + ")";
      }
      description = JavaErrorBundle.message("ambiguous.method.call", m1, m2);
      toolTip = createAmbiguousMethodHtmlTooltip(new MethodCandidateInfo[]{methodCandidate1, methodCandidate2});
    }
    else {
      if (element != null && (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect())) {
        return null;
      }
      if (candidates.length == 0) {
        return null;
      }
      if (IncompleteModelUtil.isIncompleteModel(list) &&
          ContainerUtil.exists(list.getExpressions(), IncompleteModelUtil::mayHaveUnknownTypeDueToPendingReference)) {
        return null;
      }
      String methodName = referenceToMethod.getReferenceName() + buildArgTypesList(list, true);
      description = JavaErrorBundle.message("cannot.resolve.method", methodName);
      toolTip = XmlStringUtil.escapeString(description);
    }
    if (PsiTreeUtil.hasErrorElements(list)) {
      return null;
    }
    TextRange fixRange = getFixRange(elementToHighlight);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).description(description).escapedToolTip(toolTip);
    if (!resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect() && methodCandidate2 != null) {
      HighlightFixUtil.registerAccessQuickFixAction(builder, fixRange, (PsiJvmMember)element, referenceToMethod, resolveResult.getCurrentFileResolveScope(), fixRange);
    }
    if (methodCandidate2 == null) {
      registerMethodCallIntentions(builder, methodCall, list, resolveHelper);
    }
    if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      HighlightFixUtil.registerStaticProblemQuickFixAction(builder, element, referenceToMethod);
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, builder, fixRange);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, methodCall, builder, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, builder, fixRange);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), builder, fixRange);
    PermuteArgumentsFix.registerFix(builder, methodCall, candidates, fixRange);
    registerChangeParameterClassFix(methodCall, list, builder, fixRange);
    return builder;
  }

  @NotNull
  private static Pair<MethodCandidateInfo, MethodCandidateInfo> findCandidates(JavaResolveResult @NotNull [] resolveResults) {
    MethodCandidateInfo methodCandidate1 = null;
    MethodCandidateInfo methodCandidate2 = null;
    for (JavaResolveResult result : resolveResults) {
      if (!(result instanceof MethodCandidateInfo candidate)) continue;
      if (candidate.isApplicable() && !candidate.getElement().isConstructor()) {
        if (methodCandidate1 == null) {
          methodCandidate1 = candidate;
        }
        else {
          methodCandidate2 = candidate;
          break;
        }
      }
    }
    return Pair.pair(methodCandidate1, methodCandidate2);
  }

  private static MethodCandidateInfo @NotNull [] toMethodCandidates(JavaResolveResult @NotNull [] resolveResults) {
    List<MethodCandidateInfo> candidateList = new ArrayList<>(resolveResults.length);
    for (JavaResolveResult result : resolveResults) {
      if (!(result instanceof MethodCandidateInfo candidate)) continue;
      if (candidate.isAccessible()) candidateList.add(candidate);
    }
    return candidateList.toArray(new MethodCandidateInfo[0]);
  }

  private static void registerMethodCallIntentions(@NotNull HighlightInfo.Builder builder,
                                                   @NotNull PsiMethodCallExpression methodCall,
                                                   @NotNull PsiExpressionList list,
                                                   @NotNull PsiResolveHelper resolveHelper) {
    TextRange fixRange = getFixRange(methodCall);
    PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
      PsiElement resolve = referenceExpression.resolve();
      if (resolve instanceof PsiClass psiClass &&
          psiClass.getContainingClass() != null &&
          !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
        QuickFixAction.registerQuickFixActions(builder, fixRange, JvmElementActionFactories.createModifierActions(psiClass,
                                                                                                                  MemberRequestsKt.modifierRequest(
                                                                                                                    JvmModifier.STATIC,
                                                                                                                    true)));
      }
    }
    else if (qualifierExpression instanceof PsiSuperExpression superExpression && superExpression.getQualifier() == null) {
      QualifySuperArgumentFix.registerQuickFixAction(superExpression, builder);
    }

    CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates(methodCall, false);
    IntentionAction action2 = QuickFixFactory.getInstance().createSurroundWithArrayFix(methodCall, null);
    builder.registerFix(action2, null, null, fixRange, null);

    CastMethodArgumentFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, builder, fixRange);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, builder, fixRange);

    CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(candidates, methodCall, builder, fixRange);

    WrapWithAdapterMethodCallFix.registerCastActions(methodCandidates, methodCall, builder, fixRange);
    IntentionAction action1 = QuickFixFactory.getInstance().createReplaceAddAllArrayToCollectionFix(methodCall);
    builder.registerFix(action1, null, null, fixRange, null);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(methodCandidates, methodCall, builder, fixRange);
    MethodReturnFixFactory.INSTANCE.registerCastActions(methodCandidates, methodCall, builder, fixRange);
    WrapExpressionFix.registerWrapAction(methodCandidates, list.getExpressions(), builder, fixRange);
    QualifyThisArgumentFix.registerQuickFixAction(methodCandidates, methodCall, builder, fixRange);
    registerMethodAccessLevelIntentions(methodCandidates, methodCall, list, builder, fixRange);

    if (!PermuteArgumentsFix.registerFix(builder, methodCall, methodCandidates, fixRange) &&
        !MoveParenthesisFix.registerFix(builder, methodCall, methodCandidates, fixRange)) {
      registerChangeMethodSignatureFromUsageIntentions(methodCandidates, list, builder, fixRange);
    }

    for (IntentionAction action : QuickFixFactory.getInstance().getVariableTypeFromCallFixes(methodCall, list)) {
      builder.registerFix(action, null, null, fixRange, null);
    }

    if (methodCandidates.length == 0) {
      registerStaticMethodQualifierFixes(methodCall, builder);
    }

    registerThisSuperFixes(methodCall, builder, fixRange);
    registerUsageFixes(methodCall, builder, fixRange);

    RemoveRedundantArgumentsFix.registerIntentions(methodCandidates, list, builder, fixRange);
    registerChangeParameterClassFix(methodCall, list, builder, fixRange);
  }

  private static void registerMethodAccessLevelIntentions(CandidateInfo @NotNull [] methodCandidates,
                                                          @NotNull PsiMethodCallExpression methodCall,
                                                          @NotNull PsiExpressionList exprList,
                                                          @Nullable HighlightInfo.Builder info,
                                                          @NotNull TextRange fixRange) {
    for (CandidateInfo methodCandidate : methodCandidates) {
      PsiMethod method = (PsiMethod)methodCandidate.getElement();
      if (!methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        HighlightFixUtil.registerAccessQuickFixAction(info, fixRange, method, methodCall.getMethodExpression(), methodCandidate.getCurrentFileResolveScope(),
                                                      fixRange);
      }
    }
  }

  @NotNull
  private static @NlsContexts.Tooltip String createAmbiguousMethodHtmlTooltip(MethodCandidateInfo @NotNull [] methodCandidates) {
    return JavaErrorBundle.message("ambiguous.method.html.tooltip",
                                     methodCandidates[0].getElement().getParameterList().getParametersCount() + 2,
                                   createAmbiguousMethodHtmlTooltipMethodRow(methodCandidates[0]),
                                   getContainingClassName(methodCandidates[0]),
                                   createAmbiguousMethodHtmlTooltipMethodRow(methodCandidates[1]),
                                   getContainingClassName(methodCandidates[1]));
  }

  @NotNull
  private static String getContainingClassName(@NotNull MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiClass containingClass = method.getContainingClass();
    return containingClass == null ? method.getContainingFile().getName() : HighlightUtil.formatClass(containingClass, false);
  }

  @Language("HTML")
  @NotNull
  private static String createAmbiguousMethodHtmlTooltipMethodRow(@NotNull MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
    StringBuilder ms = new StringBuilder("<td><b>" + method.getName() + "</b></td>");
    for (int j = 0; j < parameters.length; j++) {
      PsiParameter parameter = parameters[j];
      PsiType type = substitutor.substitute(parameter.getType());
      ms.append("<td><b>").append(j == 0 ? "(" : "").append(XmlStringUtil.escapeString(type.getPresentableText()))
        .append(j == parameters.length - 1 ? ")" : ",").append("</b></td>");
    }
    if (parameters.length == 0) {
      ms.append("<td><b>()</b></td>");
    }
    return ms.toString();
  }

  @NotNull
  private static @NlsContexts.Tooltip String createMismatchedArgumentsHtmlTooltip(@NotNull MethodCandidateInfo info, @NotNull PsiExpressionList list) {
    PsiMethod method = info.getElement();
    PsiSubstitutor substitutor = info.getSubstitutor();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    return createMismatchedArgumentsHtmlTooltip(list, info, parameters, substitutor);
  }

  @Language("HTML")
  @NotNull
  private static @NlsContexts.Tooltip String createMismatchedArgumentsHtmlTooltip(@NotNull PsiExpressionList list,
                                                                                  @Nullable MethodCandidateInfo info,
                                                                                  PsiParameter @NotNull [] parameters,
                                                                                  @NotNull PsiSubstitutor substitutor) {
    PsiExpression[] expressions = list.getExpressions();
    if ((parameters.length == 0 || !parameters[parameters.length - 1].isVarArgs()) &&
        parameters.length != expressions.length) {
      return createMismatchedArgumentCountTooltip(parameters.length, expressions.length);
    }

    HtmlBuilder message = new HtmlBuilder();
    message.append(getTypeMismatchTable(info, substitutor, parameters, expressions));

    String errorMessage = info != null ? info.getInferenceErrorMessage() : null;
    message.append(getTypeMismatchErrorHtml(errorMessage));
    return message.wrapWithHtmlBody().toString();
  }

  @NotNull
  private static @NlsContexts.Tooltip String createMismatchedArgumentCountTooltip(int expected, int actual) {
    return HtmlChunk.text(JavaAnalysisBundle.message("arguments.count.mismatch", expected, actual)).wrapWith("html").toString();
  }

  @NotNull
  private static HtmlChunk getTypeMismatchErrorHtml(@Nls String errorMessage) {
    if (errorMessage == null) {
      return HtmlChunk.empty();
    }
    return HtmlChunk.tag("td").style("padding-left: 4px; padding-top: 10;")
      .addText(JavaAnalysisBundle.message("type.mismatch.reason", errorMessage))
      .wrapWith("tr").wrapWith("table");
  }

  @NotNull
  private static HtmlChunk getTypeMismatchTable(@Nullable MethodCandidateInfo info,
                                                @NotNull PsiSubstitutor substitutor,
                                                PsiParameter @NotNull [] parameters,
                                                PsiExpression[] expressions) {
    String greyedColor = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    HtmlBuilder table = new HtmlBuilder();
    HtmlChunk.Element td = HtmlChunk.tag("td");
    HtmlChunk requiredHeader = td.style("color: " + greyedColor + "; padding-left: 16px; padding-right: 24px;")
      .addText(JavaAnalysisBundle.message("required.type"));
    HtmlChunk providedHeader = td.style("color: " + greyedColor + "; padding-right: 28px;")
      .addText(JavaAnalysisBundle.message("provided.type"));
    table.append(HtmlChunk.tag("tr").children(td, requiredHeader, providedHeader));

    String parameterNameStyle = String.format("color: %s; font-size:%dpt; padding:1px 4px 1px 4px;",
                                              greyedColor,
                                              StartupUiUtil.getLabelFont().getSize() - (SystemInfo.isWindows ? 0 : 1));

    Color paramBgColor = EditorColorsUtil.getGlobalOrDefaultColorScheme()
      .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
      .getBackgroundColor();
    if (paramBgColor != null) {
      parameterNameStyle += "background-color: " + ColorUtil.toHtmlColor(paramBgColor) + ";";
    }

    boolean varargAdded = false;
    for (int i = 0; i < Math.max(parameters.length, expressions.length); i++) {
      boolean varargs = info != null && info.isVarargs();
      if (assignmentCompatible(i, parameters, expressions, substitutor, varargs)) continue;
      PsiParameter parameter = null;
      if (i < parameters.length) {
        parameter = parameters[i];
        varargAdded = parameter.isVarArgs();
      }
      else if (!varargAdded) {
        parameter = parameters[parameters.length - 1];
        varargAdded = true;
      }
      PsiType parameterType = substitutor.substitute(PsiTypesUtil.getParameterType(parameters, i, varargs));
      PsiExpression expression = i < expressions.length ? expressions[i] : null;
      boolean showShortType = HighlightUtil.showShortType(parameterType,
                                                          expression != null ? expression.getType() : null);
      HtmlChunk.Element nameCell = td;
      HtmlChunk.Element typeCell = td.style("padding-left: 16px; padding-right: 24px;");
      if (parameter != null) {
        nameCell = nameCell.child(td.style(parameterNameStyle).addText(parameter.getName() + ":")
                                    .wrapWith("tr").wrapWith("table"));
        typeCell = typeCell.child(HighlightUtil.redIfNotMatch(substitutor.substitute(parameter.getType()), true, showShortType));
      }

      HtmlChunk.Element mismatchedCell = td.style("padding-right: 28px;");
      if (expression != null) {
        mismatchedCell = mismatchedCell.child(mismatchedExpressionType(parameterType, expression));
      }
      table.append(HtmlChunk.tag("tr").children(nameCell, typeCell, mismatchedCell));
    }
    return table.wrapWith("table");
  }

  @NotNull
  private static @Nls HtmlChunk mismatchedExpressionType(PsiType parameterType, @NotNull PsiExpression expression) {
    return HtmlChunk.raw(HighlightUtil.createIncompatibleTypesTooltip(parameterType, expression.getType(), new HighlightUtil.IncompatibleTypesTooltipComposer() {
      @NotNull
      @Override
      public String consume(@NotNull @NlsSafe String lRawType,
                            @NotNull @NlsSafe String lTypeArguments,
                            @NotNull @NlsSafe String rRawType,
                            @NotNull @NlsSafe String rTypeArguments) {
        return rRawType + rTypeArguments;
      }

      @Override
      public boolean skipTypeArgsColumns() {
        return true;
      }
    }));
  }

  private static boolean assignmentCompatible(int i,
                                              PsiParameter @NotNull [] parameters,
                                              PsiExpression @NotNull [] expressions,
                                              @NotNull PsiSubstitutor substitutor,
                                              boolean varargs) {
    PsiExpression expression = i < expressions.length ? expressions[i] : null;
    if (expression == null) return true;
    PsiType paramType = substitutor.substitute(PsiTypesUtil.getParameterType(parameters, i, varargs));
    return paramType != null && TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression) ||
           IncompleteModelUtil.isIncompleteModel(expression) && IncompleteModelUtil.isPotentiallyConvertible(paramType, expression);
  }

  static HighlightInfo.Builder checkMethodMustHaveBody(@NotNull PsiMethod method, @Nullable PsiClass aClass) {
    HighlightInfo.Builder errorResult = null;
    if (method.getBody() == null
        && !method.hasModifierProperty(PsiModifier.ABSTRACT)
        && !method.hasModifierProperty(PsiModifier.NATIVE)
        && aClass != null
        && !aClass.isInterface()
        && !PsiUtilCore.hasErrorElementChild(method)) {
      int start = method.getModifierList().getTextRange().getStartOffset();
      int end = Math.max(start, method.getTextRange().getEndOffset());

      String description = JavaErrorBundle.message("missing.method.body");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description);
      IntentionAction action = QuickFixFactory.getInstance().createAddMethodBodyFix(method);
      errorResult.registerFix(action, null, null, null, null);
      if (HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, method.getModifierList()) == null &&
          !(aClass instanceof PsiAnonymousClass)) {
        final List<IntentionAction> actions =
          JvmElementActionFactories.createModifierActions(method, MemberRequestsKt.modifierRequest(JvmModifier.ABSTRACT, true));
        QuickFixAction.registerQuickFixActions(errorResult, null, actions);
      }
    }
    return errorResult;
  }

  static HighlightInfo.Builder checkAbstractMethodInConcreteClass(@NotNull PsiMethod method, @NotNull PsiElement elementToHighlight) {
    HighlightInfo.Builder errorResult = null;
    PsiClass aClass = method.getContainingClass();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)
        && aClass != null
        && (aClass.isEnum() || !aClass.hasModifierProperty(PsiModifier.ABSTRACT))
        && !PsiUtilCore.hasErrorElementChild(method)) {
      if (aClass.isEnum()) {
        for (PsiField field : aClass.getFields()) {
          if (field instanceof PsiEnumConstant) {
            // only report abstract method in enum when there are no enum constants to implement it
            return null;
          }
        }
      }
      String description = JavaErrorBundle.message("abstract.method.in.non.abstract.class");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description);
      errorResult.registerFix(method.getBody() != null
                              ? QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.ABSTRACT, false, false)
                              : QuickFixFactory.getInstance().createAddMethodBodyFix(method), null, null, null, null);
      if (!aClass.isEnum()) {
        errorResult.registerFix(QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.ABSTRACT, true, false), null, null, null, null);
      }
    }
    return errorResult;
  }

  static HighlightInfo.Builder checkConstructorName(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass != null) {
      String className = aClass instanceof PsiAnonymousClass ? null : aClass.getName();
      if (className == null || !Comparing.strEqual(method.getName(), className)) {
        PsiElement element = ObjectUtils.notNull(method.getNameIdentifier(), method);
        String description = JavaErrorBundle.message("missing.return.type");
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
        if (className != null) {
          IntentionAction action = QuickFixFactory.getInstance().createRenameElementFix(method, className);
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkDuplicateMethod(@NotNull PsiClass aClass,
                                            @NotNull PsiMethod method,
                                            @NotNull MostlySingularMultiMap<MethodSignature, PsiMethod> duplicateMethods) {
    if (method instanceof ExternallyDefinedPsiElement) return null;
    MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    int methodCount = 1;
    List<PsiMethod> methods = (List<PsiMethod>)duplicateMethods.get(methodSignature);
    if (methods.size() > 1) {
      methodCount++;
    }

    if (methodCount == 1 && aClass.isEnum() && isEnumSyntheticMethod(methodSignature, aClass.getProject())) {
      methodCount++;
    }
    if (methodCount > 1) {
      String description = JavaErrorBundle.message("duplicate.method",
                                                   JavaHighlightUtil.formatMethod(method),
                                                   HighlightUtil.formatClass(aClass));
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).
        range(method, textRange.getStartOffset(), textRange.getEndOffset()).
        descriptionAndTooltip(description);
    }
    return null;
  }

  static HighlightInfo.Builder checkMethodCanHaveBody(@NotNull PsiMethod method, @NotNull LanguageLevel languageLevel) {
    PsiClass aClass = method.getContainingClass();
    boolean hasNoBody = method.getBody() == null;
    boolean isInterface = aClass != null && aClass.isInterface();
    boolean isExtension = method.hasModifierProperty(PsiModifier.DEFAULT);
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    boolean isPrivate = method.hasModifierProperty(PsiModifier.PRIVATE);
    boolean isConstructor = method.isConstructor();

    List<IntentionAction> additionalFixes = new ArrayList<>();
    String description = null;
    if (hasNoBody) {
      if (isExtension) {
        description = JavaErrorBundle.message("extension.method.should.have.a.body");
      }
      else if (isInterface) {
        if (isStatic && JavaFeature.STATIC_INTERFACE_CALLS.isSufficient(languageLevel)) {
          description = JavaErrorBundle.message("static.methods.in.interfaces.should.have.body");
        }
        else if (isPrivate && JavaFeature.PRIVATE_INTERFACE_METHODS.isSufficient(languageLevel)) {
          description = JavaErrorBundle.message("private.methods.in.interfaces.should.have.body");
        }
      }
      if (description != null) {
        additionalFixes.add(QuickFixFactory.getInstance().createAddMethodBodyFix(method));
      }
    }
    else if (isInterface) {
      if (!isExtension && !isStatic && !isPrivate && !isConstructor) {
        description = JavaErrorBundle.message("interface.methods.cannot.have.body");
        if (JavaFeature.EXTENSION_METHODS.isSufficient(languageLevel)) {
          if (Stream.of(method.findDeepestSuperMethods())
            .map(PsiMethod::getContainingClass)
            .filter(Objects::nonNull)
            .map(PsiClass::getQualifiedName)
            .noneMatch(CommonClassNames.JAVA_LANG_OBJECT::equals)) {
            IntentionAction makeDefaultFix = QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.DEFAULT, true, false);
            additionalFixes.add(PriorityIntentionActionWrapper.highPriority(makeDefaultFix));
            additionalFixes.add(QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.STATIC, true, false));
          }
        }
      }
    }
    else if (isExtension) {
      description = JavaErrorBundle.message("extension.method.in.class");
      additionalFixes.add(QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.DEFAULT, false, false));
    }
    else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      description = JavaErrorBundle.message("abstract.methods.cannot.have.a.body");
    }
    else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      description = JavaErrorBundle.message("native.methods.cannot.have.a.body");
    }
    if (description == null) return null;

    TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) && !isInterface) {
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.ABSTRACT, false, false);
      info.registerFix(action, null, null, null, null);
    }
    for (IntentionAction intentionAction : additionalFixes) {
      info.registerFix(intentionAction, null, null, null, null);
    }
    if (!hasNoBody) {
      if (!isExtension) {
        IntentionAction action = QuickFixFactory.getInstance().createDeleteMethodBodyFix(method);
        info.registerFix(action, null, null, null, null);
      }
      IntentionAction action = QuickFixFactory.getInstance().createPushDownMethodFix();
      info.registerFix(action, null, null, null, null);
    }
    return info;
  }

  static HighlightInfo.Builder checkConstructorCallProblems(@NotNull PsiMethodCallExpression methodCall) {
    if (!JavaPsiConstructorUtil.isConstructorCall(methodCall)) return null;
    PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
    PsiReferenceExpression expression = methodCall.getMethodExpression();
    if (method == null || !method.isConstructor()) {
      String message = JavaErrorBundle.message("constructor.call.only.allowed.in.constructor", expression.getText() + "()");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(message);
    }
    PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    if (constructorCall != methodCall) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(
        JavaErrorBundle.message("only.one.constructor.call.allowed.in.constructor", expression.getText() + "()"));
    }
    PsiElement codeBlock = methodCall.getParent().getParent();
    if (!(codeBlock instanceof PsiCodeBlock) || !(codeBlock.getParent() instanceof PsiMethod)) {
      String message = JavaErrorBundle.message("constructor.call.must.be.top.level.statement", expression.getText() + "()");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(message);
    }
    if (JavaPsiRecordUtil.isCompactConstructor(method) || JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
      String message = JavaErrorBundle.message("record.constructor.call.in.canonical");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(message);
    }
    PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(methodCall.getParent(), PsiStatement.class);
    if (prevStatement != null) {
      String message = JavaErrorBundle.message("constructor.call.must.be.first.statement", expression.getText() + "()");
      HighlightInfo.Builder builder =
        HighlightUtil.checkFeature(methodCall, JavaFeature.STATEMENTS_BEFORE_SUPER, PsiUtil.getLanguageLevel(methodCall),
                                   methodCall.getContainingFile(), message, HighlightInfoType.ERROR);
      if (builder != null) return builder;
    }
    if (JavaPsiConstructorUtil.isChainedConstructorCall(methodCall) && HighlightControlFlowUtil.isRecursivelyCalledConstructor(method)) {
      String description = JavaErrorBundle.message("recursive.constructor.invocation");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(description);
    }
    return null;
  }


  static HighlightInfo.Builder checkSuperAbstractMethodDirectCall(@NotNull PsiMethodCallExpression methodCallExpression) {
    PsiReferenceExpression expression = methodCallExpression.getMethodExpression();
    if (!(expression.getQualifierExpression() instanceof PsiSuperExpression)) return null;
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method != null && method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String message = JavaErrorBundle.message("direct.abstract.method.access", JavaHighlightUtil.formatMethod(method));
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCallExpression).descriptionAndTooltip(message);
      IntentionAction action1 = QuickFixFactory.getInstance().createDeleteFix(methodCallExpression);
      info.registerFix(action1, null, null, null, null);
      int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
      String name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, 0);
      String modifierText = VisibilityUtil.toPresentableText(PsiModifier.ABSTRACT);
      String text = QuickFixBundle.message("remove.modifier.fix", name, modifierText);
      IntentionAction action = QuickFixFactory.getInstance().createAddMethodBodyFix(method, text);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkConstructorCallsBaseClassConstructor(@NotNull PsiMethod constructor,
                                                                         @NotNull PsiResolveHelper resolveHelper) {
    if (!constructor.isConstructor()) return null;
    PsiClass aClass = constructor.getContainingClass();
    if (aClass == null) return null;
    if (aClass.isEnum()) return null;
    PsiCodeBlock body = constructor.getBody();
    if (body == null) return null;

    if (JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor) != null) return null;
    TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(constructor);
    PsiClassType[] handledExceptions = constructor.getThrowsList().getReferencedTypes();
    HighlightInfo.Builder info = HighlightClassUtil.checkBaseClassDefaultConstructorProblem(aClass, resolveHelper, textRange, handledExceptions);
    if (info != null) {
      IntentionAction action2 = QuickFixFactory.getInstance().createInsertSuperFix(constructor);
      info.registerFix(action2, null, null, null, null);
      IntentionAction action1 = QuickFixFactory.getInstance().createInsertThisFix(constructor);
      info.registerFix(action1, null, null, null, null);
      PsiClass superClass = aClass.getSuperClass();
      if (superClass != null) {
        IntentionAction action = QuickFixFactory.getInstance().createAddDefaultConstructorFix(superClass);
        info.registerFix(action, null, null, null, null);
      }
    }
    return info;
  }


  /**
   * @return error if static method overrides instance method or
   *         instance method overrides static. see JLS 8.4.6.1, 8.4.6.2
   */
  static HighlightInfo.Builder checkStaticMethodOverride(@NotNull PsiMethod method, @NotNull PsiFile containingFile) {
    // constructors are not members and therefore don't override class methods
    if (method.isConstructor()) {
      return null;
    }

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    HierarchicalMethodSignature methodSignature = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method);
    List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
    if (superSignatures.isEmpty()) {
      return null;
    }

    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    for (HierarchicalMethodSignature signature : superSignatures) {
      PsiMethod superMethod = signature.getMethod();
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      HighlightInfo.Builder highlightInfo = checkStaticMethodOverride(aClass, method, isStatic, superClass, superMethod, containingFile);
      if (highlightInfo != null) {
        return highlightInfo;
      }
    }

    return null;
  }

  private static HighlightInfo.Builder checkStaticMethodOverride(@NotNull PsiClass aClass,
                                                         @NotNull PsiMethod method,
                                                         boolean isMethodStatic,
                                                         @NotNull PsiClass superClass,
                                                         @NotNull PsiMethod superMethod,
                                                         @NotNull PsiFile containingFile) {
    PsiManager manager = containingFile.getManager();
    PsiModifierList superModifierList = superMethod.getModifierList();
    PsiModifierList modifierList = method.getModifierList();
    if (superModifierList.hasModifierProperty(PsiModifier.PRIVATE)) return null;
    if (superModifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
        && !JavaPsiFacade.getInstance(manager.getProject()).arePackagesTheSame(aClass, superClass)) {
      return null;
    }
    boolean isSuperMethodStatic = superModifierList.hasModifierProperty(PsiModifier.STATIC);
    if (isMethodStatic != isSuperMethodStatic) {
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      String messageKey = isMethodStatic
                                ? "static.method.cannot.override.instance.method"
                                : "instance.method.cannot.override.static.method";

      String description = JavaErrorBundle.message(messageKey,
                                                   JavaHighlightUtil.formatMethod(method),
                                                   HighlightUtil.formatClass(aClass),
                                                   JavaHighlightUtil.formatMethod(superMethod),
                                                   HighlightUtil.formatClass(superClass));

      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
      if (!isSuperMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, modifierList) == null) {
        IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.STATIC, isSuperMethodStatic, false);
        info.registerFix(action, null, null, null, null);
      }
      if (manager.isInProject(superMethod) &&
          (!isMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, superModifierList) == null)) {
        QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(superMethod, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, isMethodStatic)));
      }
      return info;
    }

    if (isMethodStatic) {
      if (superClass.isInterface()) return null;
      int accessLevel = PsiUtil.getAccessLevel(modifierList);
      String accessModifier = PsiUtil.getAccessModifier(accessLevel);
      HighlightInfo.Builder info = isWeaker(method, modifierList, accessModifier, accessLevel, superMethod, true, null);
      if (info != null) return info;
      info = checkSuperMethodIsFinal(method, superMethod);
      return info;
    }
    return null;
  }

  private static String checkInterfaceInheritedMethodsReturnTypesDescription(@NotNull List<? extends MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                                             @NotNull LanguageLevel languageLevel) {
    if (superMethodSignatures.size() < 2) return null;
    MethodSignatureBackedByPsiMethod[] returnTypeSubstitutable = {superMethodSignatures.get(0)};
    for (int i = 1; i < superMethodSignatures.size(); i++) {
      PsiMethod currentMethod = returnTypeSubstitutable[0].getMethod();
      PsiType currentType = returnTypeSubstitutable[0].getSubstitutor().substitute(currentMethod.getReturnType());

      MethodSignatureBackedByPsiMethod otherSuperSignature = superMethodSignatures.get(i);
      PsiMethod otherSuperMethod = otherSuperSignature.getMethod();
      PsiSubstitutor otherSubstitutor = otherSuperSignature.getSubstitutor();
      PsiType otherSuperReturnType = otherSubstitutor.substitute(otherSuperMethod.getReturnType());
      PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(returnTypeSubstitutable[0],
                                                                                                  otherSuperSignature);
      if (unifyingSubstitutor != null) {
        otherSuperReturnType = unifyingSubstitutor.substitute(otherSuperReturnType);
        currentType = unifyingSubstitutor.substitute(currentType);
      }

      if (otherSuperReturnType == null || currentType == null || otherSuperReturnType.equals(currentType)) continue;
      PsiType otherReturnType = otherSuperReturnType;
      PsiType curType = currentType;
      String info =
        LambdaUtil.performWithSubstitutedParameterBounds(otherSuperMethod.getTypeParameters(), otherSubstitutor, () -> {
          if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
            //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.4.8 Example 8.1.5-3
            if (!(otherReturnType instanceof PsiPrimitiveType || curType instanceof PsiPrimitiveType)) {
              if (otherReturnType.isAssignableFrom(curType)) return null;
              if (curType.isAssignableFrom(otherReturnType)) {
                returnTypeSubstitutable[0] = otherSuperSignature;
                return null;
              }
            }
            if (otherSuperMethod.getTypeParameters().length > 0 && JavaGenericsUtil.isRawToGeneric(otherReturnType, curType)) return null;
          }
          return MessageFormat.format("{0}; {1}", createClashMethodMessage(otherSuperMethod, currentMethod, true), JavaErrorBundle.message("unrelated.overriding.methods.return.types"));
        });
      if (info != null) return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkOverrideEquivalentInheritedMethods(@NotNull PsiClass aClass,
                                                               @NotNull PsiFile containingFile,
                                                               @NotNull LanguageLevel languageLevel) {
    Collection<HierarchicalMethodSignature> visibleSignatures = aClass.getVisibleSignatures();
    if (aClass.getImplementsListTypes().length == 0 && aClass.getExtendsListTypes().length == 0) {
      // optimization: do not analyze unrelated methods from Object: in case of no inheritance they can't conflict
      return null;
    }
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();

    String description = null;
    boolean appendImplementMethodFix = true;
    Ultimate:
    for (HierarchicalMethodSignature signature : visibleSignatures) {
      PsiMethod method = signature.getMethod();
      if (!resolveHelper.isAccessible(method, aClass, null)) continue;
      List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

      boolean allAbstracts = method.hasModifierProperty(PsiModifier.ABSTRACT);
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || aClass.equals(containingClass)) continue; //to be checked at method level

      if (aClass.isInterface() && !containingClass.isInterface()) continue;
      String error;
      if (allAbstracts) {
        superSignatures = new ArrayList<>(superSignatures);
        superSignatures.add(0, signature);
        error = checkInterfaceInheritedMethodsReturnTypesDescription(superSignatures, languageLevel);
      }
      else {
        Ref<String> descriptionH = new Ref<>();
        checkMethodIncompatibleReturnType(signature, superSignatures, false, descriptionH);
        error = descriptionH.get();
      }
      if (error != null) {
        description = error;
      }

      if (method.hasModifierProperty(PsiModifier.STATIC) &&
          //jsl 8, chapter 9.4.1
          //chapter 8.4.8.2 speaks about a class that "declares or inherits a static method",
          // at the same time the rule from chapter 9.4.1 speaks only about an interface that "declares a static method"
          //There is no point to add java version check, because static methods in interfaces are allowed from java 8 too.
          (!aClass.isInterface() ||
           aClass.getManager().areElementsEquivalent(aClass, method.getContainingClass()))) {
        for (HierarchicalMethodSignature superSignature : superSignatures) {
          PsiMethod superMethod = superSignature.getMethod();
          if (!superMethod.hasModifierProperty(PsiModifier.STATIC)) {
            PsiClass superClass = superMethod.getContainingClass();
            description = JavaErrorBundle.message("static.method.cannot.override.instance.method",
                                                  JavaHighlightUtil.formatMethod(method),
                                                  HighlightUtil.formatClass(containingClass),
                                                  JavaHighlightUtil.formatMethod(superMethod),
                                                    superClass != null ? HighlightUtil.formatClass(superClass) : "<unknown>");
            appendImplementMethodFix = false;
            break Ultimate;
          }
        }
        continue;
      }

      if (description == null) {
        Ref<@Nls String> descriptionH = new Ref<>();
        checkMethodIncompatibleThrows(signature, superSignatures, false, aClass, descriptionH);
        description = descriptionH.get();
      }

      if (description == null) {
        Ref<@Nls String> descriptionH = new Ref<>();
        checkMethodWeakerPrivileges(signature, superSignatures, false, containingFile, descriptionH);
        description = descriptionH.get();
      }

      if (description != null) break;
    }


    if (description != null) {
      // show error info at the class level
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      HighlightInfo.Builder highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
      if (appendImplementMethodFix) {
        IntentionAction action = QuickFixFactory.getInstance().createImplementMethodsFix(aClass);
        highlightInfo.registerFix(action, null, null, null, null);
      }
      return highlightInfo;
    }
    return null;
  }

  static HighlightInfo.Builder checkConstructorHandleSuperClassExceptions(@NotNull PsiMethod method) {
    if (!method.isConstructor()) {
      return null;
    }
    PsiCodeBlock body = method.getBody();
    PsiStatement[] statements = body == null ? null : body.getStatements();
    if (statements == null) return null;

    // if we have unhandled exception inside method body, we could not have been called here,
    // so the only problem it can catch here is with super ctr only
    Collection<PsiClassType> unhandled = ExceptionUtil.collectUnhandledExceptions(method, method.getContainingClass());
    if (unhandled.isEmpty()) return null;
    String description = HighlightUtil.getUnhandledExceptionsDescriptor(unhandled);
    TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
    HighlightInfo.Builder highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
    for (PsiClassType exception : unhandled) {
      IntentionAction action =
        new LocalQuickFixOnPsiElementAsIntentionAdapter(QuickFixFactory.getInstance().createMethodThrowsFix(method, exception, true, false));
      highlightInfo.registerFix(action, null, null, null, null);
    }
    return highlightInfo;
  }

  @NotNull
  public static TextRange getFixRange(@NotNull PsiElement element) {
    PsiElement nextSibling = element.getNextSibling();
    TextRange range = element.getTextRange();
    if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.SEMICOLON)) {
      return range.grown(1);
    }
    return range;
  }

  static void checkNewExpression(@NotNull Project project, @NotNull PsiNewExpression expression,
                                 @Nullable PsiType type,
                                 @NotNull JavaSdkVersion javaSdkVersion, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (!(type instanceof PsiClassType classType)) return;
    PsiClassType.ClassResolveResult typeResult = classType.resolveGenerics();
    PsiClass aClass = typeResult.getElement();
    if (aClass == null) return;
    if (aClass instanceof PsiAnonymousClass anonymousClass) {
      classType = anonymousClass.getBaseClassType();
      typeResult = classType.resolveGenerics();
      aClass = typeResult.getElement();
      if (aClass == null) return;
    }

    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    checkConstructorCall(project, typeResult, expression, classType, classReference, javaSdkVersion, expression.getArgumentList(), errorSink);
  }

  static void checkAmbiguousConstructorCall(@NotNull Project project, PsiJavaCodeReferenceElement ref,
                                            PsiElement resolved,
                                            PsiElement parent,
                                            JavaSdkVersion version, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (resolved instanceof PsiClass psiClass &&
        parent instanceof PsiNewExpression newExpression && psiClass.getConstructors().length > 0) {
      if (newExpression.resolveMethod() == null && !PsiTreeUtil.findChildrenOfType(newExpression.getArgumentList(), PsiFunctionalExpression.class).isEmpty()) {
        PsiType type = newExpression.getType();
        if (type instanceof PsiClassType classType) {
          checkConstructorCall(project, classType.resolveGenerics(), newExpression, type, newExpression.getClassReference(), version, ref,
                               errorSink);
        }
      }
    }
  }

  static void checkConstructorCall(@NotNull Project project, @NotNull PsiClassType.ClassResolveResult typeResolveResult,
                                   @NotNull PsiConstructorCall constructorCall,
                                   @NotNull PsiType type,
                                   @Nullable PsiJavaCodeReferenceElement classReference,
                                   @NotNull JavaSdkVersion javaSdkVersion,
                                   @Nullable PsiElement elementToHighlight,
                                   @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (elementToHighlight == null) return;
    PsiExpressionList list = constructorCall.getArgumentList();
    if (list == null) return;
    PsiClass aClass = typeResolveResult.getElement();
    if (aClass == null) return;
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
    PsiClass accessObjectClass = null;
    if (constructorCall instanceof PsiNewExpression newExpression) {
      PsiExpression qualifier = newExpression.getQualifier();
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }
    }
    if (classReference != null && !resolveHelper.isAccessible(aClass, constructorCall, accessObjectClass)) {
      String description = HighlightUtil.accessProblemDescription(classReference, aClass, typeResolveResult);
      PsiElement element = ObjectUtils.notNull(classReference.getReferenceNameElement(), classReference);
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
      HighlightFixUtil.registerAccessQuickFixAction(info, element.getTextRange(), aClass, classReference, null, null);
      errorSink.accept(info);
      return;
    }
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) {
      if (!list.isEmpty()) {
        String constructorName = aClass.getName();
        String argTypes = buildArgTypesList(list, false);
        String description = JavaErrorBundle.message("wrong.constructor.arguments", constructorName + "()", argTypes);
        String tooltip = createMismatchedArgumentsHtmlTooltip(list, null, PsiParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).description(description).escapedToolTip(tooltip).navigationShift(+1);
        if (classReference != null) {
          ConstructorParametersFixer.registerFixActions(classReference, constructorCall, info, getFixRange(list));
        }
        TextRange textRange = constructorCall.getTextRange();
        QuickFixAction.registerQuickFixActions(
          info, textRange, QuickFixFactory.getInstance().createCreateConstructorFromUsageFixes(constructorCall)
        );
        RemoveRedundantArgumentsFix.registerIntentions(list, info, getFixRange(list));
        errorSink.accept(info);
        return;
      }
      if (classReference != null && aClass.hasModifierProperty(PsiModifier.PROTECTED) && callingProtectedConstructorFromDerivedClass(constructorCall, aClass)) {
        HighlightInfo.Builder info = buildAccessProblem(classReference, aClass, typeResolveResult);
        errorSink.accept(info);
      }
      else if (aClass.isInterface() && constructorCall instanceof PsiNewExpression newExpression) {
        PsiReferenceParameterList typeArgumentList = newExpression.getTypeArgumentList();
        if (typeArgumentList.getTypeArguments().length > 0) {
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeArgumentList)
            .descriptionAndTooltip(JavaErrorBundle.message("anonymous.class.implements.interface.cannot.have.type.arguments"));
          errorSink.accept(info);
        }
      }
      return;
    }

    PsiElement place = list;
    if (constructorCall instanceof PsiNewExpression newExpression) {
      PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass != null) place = anonymousClass;
    }

    JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, place);
    MethodCandidateInfo result = null;
    if (results.length == 1) result = (MethodCandidateInfo)results[0];

    PsiMethod constructor = result == null ? null : result.getElement();

    boolean applicable = true;
    try {
      PsiDiamondType diamondType = constructorCall instanceof PsiNewExpression newExpression ? PsiDiamondType.getDiamondType(newExpression) : null;
      JavaResolveResult staticFactory = diamondType != null ? diamondType.getStaticFactory() : null;
      if (staticFactory instanceof MethodCandidateInfo info) {
        if (info.isApplicable()) {
          result = info;
          if (constructor == null) {
            constructor = info.getElement();
          }
        }
        else {
          applicable = false;
        }
      }
      else {
        applicable = result != null && result.isApplicable();
      }
    }
    catch (IndexNotReadyException ignored) {
    }
    boolean reported = false;
    if (constructor == null) {
      if (IncompleteModelUtil.isIncompleteModel(list) &&
          ContainerUtil.exists(results, r -> r instanceof MethodCandidateInfo info && info.isPotentiallyCompatible() == ThreeState.YES) &&
          ContainerUtil.exists(list.getExpressions(), e -> IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(e))) {
        return;
      }
      String name = aClass.getName();
      name += buildArgTypesList(list, true);
      String description = JavaErrorBundle.message("cannot.resolve.constructor", name);
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(elementToHighlight).descriptionAndTooltip(description);
      TextRange fixRange = getFixRange(elementToHighlight);
      WrapExpressionFix.registerWrapAction(results, list.getExpressions(), info, fixRange);
      registerFixesOnInvalidConstructorCall(info, constructorCall, classReference, list, aClass, constructors, results, fixRange);
      errorSink.accept(info);
      reported = true;
    }
    else if (classReference != null &&
             (!result.isAccessible() ||
              constructor.hasModifierProperty(PsiModifier.PROTECTED) && callingProtectedConstructorFromDerivedClass(constructorCall, aClass))) {
      HighlightInfo.Builder info = buildAccessProblem(classReference, constructor, result);
      errorSink.accept(info);
      reported = true;
    }
    else if (!applicable) {
      HighlightInfo.Builder info = createIncompatibleCallHighlightInfo(list, result, errorSink);
      if (info != null) {
        JavaResolveResult[] methodCandidates = results;
        if (constructorCall instanceof PsiNewExpression newExpression) {
          methodCandidates = resolveHelper.getReferencedMethodCandidates(newExpression, true);
        }
        registerFixesOnInvalidConstructorCall(info, constructorCall, classReference, list, aClass, constructors, methodCandidates, getFixRange(list));
        registerMethodReturnFixAction(info, result, constructorCall);
        errorSink.accept(info);
        reported = true;
      }
    }
    else if (constructorCall instanceof PsiNewExpression newExpression) {
      PsiReferenceParameterList typeArgumentList = newExpression.getTypeArgumentList();
      HighlightInfo.Builder info = GenericsHighlightUtil.checkReferenceTypeArgumentList(constructor, typeArgumentList, result.getSubstitutor(), false, javaSdkVersion);
      if (info != null) {
        errorSink.accept(info);
        reported = true;
      }
    }

    HighlightInfo.Builder info = result == null || reported ? null : checkVarargParameterErasureToBeAccessible(result, constructorCall);
    if (result != null && info == null && !reported) {
      info = createIncompatibleTypeHighlightInfo(constructorCall, resolveHelper, result, constructorCall);
    }
    errorSink.accept(info);
  }

  /**
   * If the compile-time declaration is applicable by variable arity invocation,
   * then where the last formal parameter type of the invocation type of the method is Fn[],
   * it is a compile-time error if the type which is the erasure of Fn is not accessible at the point of invocation.
   */
  private static HighlightInfo.Builder checkVarargParameterErasureToBeAccessible(@NotNull MethodCandidateInfo info, @NotNull PsiCall place) {
    PsiMethod method = info.getElement();
    if (info.isVarargs() || method.isVarArgs() && !PsiUtil.isLanguageLevel8OrHigher(place)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiType componentType = ((PsiEllipsisType)parameters[parameters.length - 1].getType()).getComponentType();
      PsiType substitutedTypeErasure = TypeConversionUtil.erasure(info.getSubstitutor().substitute(componentType));
      PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(substitutedTypeErasure);
      if (targetClass != null && !PsiUtil.isAccessible(targetClass, place, null)) {
        PsiExpressionList argumentList = place.getArgumentList();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .descriptionAndTooltip(JavaErrorBundle.message("formal.varargs.element.type.inaccessible.here",
                                                         PsiFormatUtil.formatClass(targetClass, PsiFormatUtilBase.SHOW_FQ_NAME)))
          .range(argumentList != null ? argumentList : place);
      }
    }
    return null;
  }

  private static HighlightInfo.Builder checkInferredReturnTypeAccessible(@NotNull MethodCandidateInfo info, @NotNull PsiMethodCallExpression methodCall) {
    PsiMethod method = info.getElement();
    PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
    if (targetClass instanceof PsiTypeParameter typeParameter && typeParameter.getOwner() == method) {
      PsiClass inferred = PsiUtil.resolveClassInClassTypeOnly(info.getSubstitutor().substitute(typeParameter));
      if (inferred != null && !PsiUtil.isAccessible(inferred, methodCall, null)) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .descriptionAndTooltip(JavaErrorBundle.message("inaccessible.type",
                                                         PsiFormatUtil.formatClass(inferred, PsiFormatUtilBase.SHOW_FQ_NAME | PsiFormatUtilBase.SHOW_NAME)))
          .range(methodCall.getArgumentList());
      }
    }
    return null;
  }

  private static void registerFixesOnInvalidConstructorCall(@NotNull HighlightInfo.Builder builder, @NotNull PsiConstructorCall constructorCall,
                                                            @Nullable PsiJavaCodeReferenceElement classReference,
                                                            @NotNull PsiExpressionList list,
                                                            @NotNull PsiClass aClass,
                                                            PsiMethod @NotNull [] constructors,
                                                            JavaResolveResult @NotNull [] results,
                                                            TextRange fixRange) {
    if (classReference != null) {
      ConstructorParametersFixer.registerFixActions(classReference, constructorCall, builder, fixRange);
      ChangeTypeArgumentsFix.registerIntentions(results, list, builder, aClass, fixRange);
    }
    else if (aClass.isEnum()) {
      ConstructorParametersFixer.registerFixActions(aClass, PsiSubstitutor.EMPTY, constructorCall, builder, fixRange);
    }
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(constructors, constructorCall, builder, fixRange);
    IntentionAction action = QuickFixFactory.getInstance().createSurroundWithArrayFix(constructorCall, null);
    builder.registerFix(action, null, null, fixRange, null);
    if (!PermuteArgumentsFix.registerFix(builder, constructorCall, toMethodCandidates(results), fixRange)) {
      registerChangeMethodSignatureFromUsageIntentions(results, list, builder, fixRange);
    }
    QuickFixAction.registerQuickFixActions(
      builder, constructorCall.getTextRange(), QuickFixFactory.getInstance().createCreateConstructorFromUsageFixes(constructorCall)
    );
    registerChangeParameterClassFix(constructorCall, list, builder, fixRange);
    RemoveRedundantArgumentsFix.registerIntentions(results, list, builder, fixRange);
  }

  @NotNull
  private static HighlightInfo.Builder buildAccessProblem(@NotNull PsiJavaCodeReferenceElement ref,
                                                          @NotNull PsiJvmMember resolved,
                                                          @NotNull JavaResolveResult result) {
    String description = HighlightUtil.accessProblemDescription(ref, resolved, result);
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description).navigationShift(+1);
    if (result.isStaticsScopeCorrect()) {
      HighlightFixUtil.registerAccessQuickFixAction(info, ref.getTextRange(), resolved, ref, result.getCurrentFileResolveScope(), null);
    }
    return info;
  }

  private static boolean callingProtectedConstructorFromDerivedClass(@NotNull PsiConstructorCall place, @NotNull PsiClass constructorClass) {
    // indirect instantiation via anonymous class is ok
    if (place instanceof PsiNewExpression newExpression && newExpression.getAnonymousClass() != null) return false;
    PsiElement curElement = place;
    PsiClass containingClass = constructorClass.getContainingClass();
    while (true) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(curElement, PsiClass.class);
      if (aClass == null) return false;
      curElement = aClass;
      if ((aClass.isInheritor(constructorClass, true) || containingClass != null && aClass.isInheritor(containingClass, true))
          && !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, constructorClass)) {
        return true;
      }
    }
  }

  @NotNull
  private static String buildArgTypesList(@NotNull PsiExpressionList list, boolean shortNames) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    PsiExpression[] args = list.getExpressions();
    for (int i = 0; i < args.length; i++) {
      if (i > 0) builder.append(", ");
      PsiType argType = args[i].getType();
      builder.append(argType != null ? (shortNames ? argType.getPresentableText() : JavaHighlightUtil.formatType(argType)) : "?");
    }
    builder.append(")");
    return builder.toString();
  }

  private static void registerChangeParameterClassFix(@NotNull PsiCall methodCall,
                                                      @NotNull PsiExpressionList list,
                                                      @Nullable HighlightInfo.Builder highlightInfo, TextRange fixRange) {
    JavaResolveResult result = methodCall.resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != expressions.length) return;
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      PsiParameter parameter = parameters[i];
      PsiType expressionType = expression.getType();
      PsiType parameterType = substitutor.substitute(parameter.getType());
      if (expressionType == null || expressionType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(expressionType) || expressionType instanceof PsiArrayType) continue;
      if (parameterType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(parameterType) || parameterType instanceof PsiArrayType) continue;
      if (parameterType.isAssignableFrom(expressionType)) continue;
      PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
      PsiClass expressionClass = PsiUtil.resolveClassInType(expressionType);
      if (parameterClass == null || expressionClass == null) continue;
      if (expressionClass instanceof PsiAnonymousClass) continue;
      if (expressionClass.isInheritor(parameterClass, true)) continue;
      IntentionAction action = QuickFixFactory.getInstance().createChangeParameterClassFix(expressionClass, (PsiClassType)parameterType);
      if (highlightInfo != null) {
        highlightInfo.registerFix(action, null, null, fixRange, null);
      }
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntentions(JavaResolveResult @NotNull [] candidates,
                                                                       @NotNull PsiExpressionList list,
                                                                       @NotNull HighlightInfo.Builder builder,
                                                                       @Nullable TextRange fixRange) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerChangeMethodSignatureFromUsageIntention(expressions, builder, fixRange, candidate, list);
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntention(PsiExpression @NotNull [] expressions,
                                                                      @NotNull HighlightInfo.Builder builder,
                                                                      @Nullable TextRange fixRange,
                                                                      @NotNull JavaResolveResult candidate,
                                                                      @NotNull PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method != null && context.getManager().isInProject(method)) {
      IntentionAction fix = QuickFixFactory.getInstance()
        .createChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context, false, 2);
      builder.registerFix(fix, null, null, fixRange, null);
      IntentionAction f2 =
        QuickFixFactory.getInstance()
          .createChangeMethodSignatureFromUsageReverseOrderFix(method, expressions, substitutor, context, false, 2);
      builder.registerFix(f2, null, null, fixRange, null);
    }
  }

  static PsiType determineReturnType(@NotNull PsiMethod method) {
    PsiManager manager = method.getManager();
    PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(method);
    if (returnStatements.length == 0) return PsiTypes.voidType();
    PsiType expectedType = null;
    for (PsiReturnStatement returnStatement : returnStatements) {
      ReturnModel returnModel = ReturnModel.create(returnStatement);
      if (returnModel == null) return null;
      expectedType = lub(expectedType, returnModel.myLeastType, returnModel.myType, method, manager);
    }
    return expectedType;
  }

  @NotNull
  private static PsiType lub(@Nullable PsiType currentType,
                             @NotNull PsiType leastValueType,
                             @NotNull PsiType valueType,
                             @NotNull PsiMethod method,
                             @NotNull PsiManager manager) {
    if (currentType == null || PsiTypes.voidType().equals(currentType)) return valueType;
    if (currentType == valueType) return currentType;

    if (TypeConversionUtil.isPrimitiveAndNotNull(valueType)) {
      if (TypeConversionUtil.isPrimitiveAndNotNull(currentType)) {
        int r1 = TypeConversionUtil.getTypeRank(currentType);
        int r2 = TypeConversionUtil.getTypeRank(leastValueType);
        return r1 >= r2 ? currentType : valueType;
      }
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(currentType);
      if (valueType.equals(unboxedType)) return currentType;
      PsiClassType boxedType = ((PsiPrimitiveType)valueType).getBoxedType(method);
      if (boxedType == null) return valueType;
      valueType = boxedType;
    }

    if (TypeConversionUtil.isPrimitiveAndNotNull(currentType)) {
      currentType = ((PsiPrimitiveType)currentType).getBoxedType(method);
    }

    return Objects.requireNonNullElse(GenericsUtil.getLeastUpperBound(currentType, valueType, manager), Objects.requireNonNullElse(currentType, valueType));
  }

  static HighlightInfo.Builder checkRecordAccessorDeclaration(@NotNull PsiMethod method) {
    PsiRecordComponent component = JavaPsiRecordUtil.getRecordComponentForAccessor(method);
    if (component == null) return null;
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return null;
    PsiType componentType = component.getType();
    PsiType methodType = method.getReturnType();
    if (methodType == null) return null; // Either constructor or incorrect method, will be reported in another way
    if (componentType instanceof PsiEllipsisType ellipsisType) {
      componentType = ellipsisType.getComponentType().createArrayType();
    }
    if (!componentType.equals(methodType)) {
      String message =
        JavaErrorBundle.message("record.accessor.wrong.return.type", componentType.getPresentableText(), methodType.getPresentableText());
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
        Objects.requireNonNull(method.getReturnTypeElement())).descriptionAndTooltip(message);
      IntentionAction action = QuickFixFactory.getInstance().createMethodReturnFix(method, componentType, false);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    return checkRecordSpecialMethodDeclaration(method, JavaErrorBundle.message("record.accessor"));
  }

  static void checkRecordConstructorDeclaration(@NotNull PsiMethod method, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (!method.isConstructor()) return;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return;
    if (!aClass.isRecord()) {
      if (JavaPsiRecordUtil.isCompactConstructor(method)) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
          identifier).descriptionAndTooltip(JavaErrorBundle.message("compact.constructor.in.regular.class"));
        IntentionAction action = QuickFixFactory.getInstance().createAddParameterListFix(method);
        info.registerFix(action, null, null, null, null);
        errorSink.accept(info);
      }
      return;
    }
    if (JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiRecordComponent[] components = aClass.getRecordComponents();
      assert parameters.length == components.length;
      for (int i = 0; i < parameters.length; i++) {
        PsiType componentType = components[i].getType();
        PsiType parameterType = parameters[i].getType();
        String componentName = components[i].getName();
        String parameterName = parameters[i].getName();
        if (!parameterType.equals(componentType)) {
          String message =
            JavaErrorBundle.message("record.canonical.constructor.wrong.parameter.type", componentName,
                                    componentType.getPresentableText(), parameterType.getPresentableText());
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
            Objects.requireNonNull(parameters[i].getTypeElement())).descriptionAndTooltip(message);
          IntentionAction action = QuickFixFactory.getInstance().createMethodParameterTypeFix(method, i, componentType, false);
          info.registerFix(action, null, null, null, null);
          errorSink.accept(info);
        }
        if (!parameterName.equals(componentName)) {
          String message = JavaErrorBundle.message("record.canonical.constructor.wrong.parameter.name", componentName, parameterName);
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
            Objects.requireNonNull(parameters[i].getNameIdentifier())).descriptionAndTooltip(message);
          if (!ContainerUtil.exists(parameters, parameter -> parameter.getName().equals(componentName))) {
            IntentionAction action = QuickFixFactory.getInstance().createRenameElementFix(parameters[i], componentName);
            info.registerFix(action, null, null, null, null);
          }
          errorSink.accept(info);
        }
      }
      HighlightInfo.Builder builder = checkRecordSpecialMethodDeclaration(method, JavaErrorBundle.message("record.canonical.constructor"));
      errorSink.accept(builder);
      return;
    }
    if (JavaPsiRecordUtil.isCompactConstructor(method)) {
      HighlightInfo.Builder info = checkRecordSpecialMethodDeclaration(method, JavaErrorBundle.message("record.compact.constructor"));
      errorSink.accept(info);
      return;
    }
    // Non-canonical constructor
    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    if (call == null || JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
      String message = JavaErrorBundle.message("record.no.constructor.call.in.non.canonical");
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(message);
      errorSink.accept(info);
    }
  }

  @Nullable
  private static HighlightInfo.Builder checkRecordSpecialMethodDeclaration(@NotNull PsiMethod method, @NotNull @Nls String methodTitle) {
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return null;
    PsiTypeParameterList typeParameterList = method.getTypeParameterList();
    if (typeParameterList != null && typeParameterList.getTypeParameters().length > 0) {
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeParameterList)
        .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.type.parameters", methodTitle));
      IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(typeParameterList);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    if (method.isConstructor()) {
      AccessModifier modifier = AccessModifier.fromModifierList(method.getModifierList());
      PsiModifierList classModifierList = Objects.requireNonNull(method.getContainingClass()).getModifierList();
      if (classModifierList != null) {
        AccessModifier classModifier = AccessModifier.fromModifierList(classModifierList);
        if (classModifier.isWeaker(modifier)) {
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
            .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.stronger.access", methodTitle, classModifier));
          IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(
            method, classModifier.toPsiModifier(), true, false);
          info.registerFix(action, null, null, null, null);
          return info;
        }
      }
    } else if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
        .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.non.public", methodTitle));
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.PUBLIC, true, false);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    PsiReferenceList throwsList = method.getThrowsList();
    if (throwsList.getReferenceElements().length > 0) {
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(throwsList.getFirstChild())
        .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.throws", StringUtil.decapitalize(methodTitle)));
      IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(throwsList);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    return null;
  }

  public static boolean isEnumSyntheticMethod(@NotNull MethodSignature methodSignature, @NotNull Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString},
                                                                              PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    return MethodSignatureUtil.areSignaturesErasureEqual(valueOfMethod, methodSignature);
  }

  private static final class ReturnModel {
    final PsiReturnStatement myStatement;
    final PsiType myType;
    final PsiType myLeastType;

    @Contract(pure = true)
    private ReturnModel(@NotNull PsiReturnStatement statement, @NotNull PsiType type) {
      myStatement = statement;
      myType = myLeastType = type;
    }

    @Contract(pure = true)
    private ReturnModel(@NotNull PsiReturnStatement statement, @NotNull PsiType type, @NotNull PsiType leastType) {
      myStatement = statement;
      myType = type;
      myLeastType = leastType;
    }

    @Nullable
    private static ReturnModel create(@NotNull PsiReturnStatement statement) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) return new ReturnModel(statement, PsiTypes.voidType());
      if (ExpressionUtils.nonStructuralChildren(value).anyMatch(c -> c instanceof PsiFunctionalExpression)) return null;
      PsiType type = RefactoringChangeUtil.getTypeByExpression(value);
      if (type == null || type instanceof PsiClassType classType && classType.resolve() == null) return null;
      return new ReturnModel(statement, type, getLeastValueType(value, type));
    }

    @NotNull
    private static PsiType getLeastValueType(@NotNull PsiExpression returnValue, @NotNull PsiType type) {
      if (type instanceof PsiPrimitiveType) {
        int rank = TypeConversionUtil.getTypeRank(type);
        if (rank < TypeConversionUtil.BYTE_RANK || rank > TypeConversionUtil.INT_RANK) return type;
        PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(returnValue.getProject()).getConstantEvaluationHelper();
        Object res = evaluator.computeConstantExpression(returnValue);
        if (res instanceof Number number) {
          long value = number.longValue();
          if (-128 <= value && value <= 127) return PsiTypes.byteType();
          if (-32768 <= value && value <= 32767) return PsiTypes.shortType();
          if (0 <= value && value <= 0xFFFF) return PsiTypes.charType();
        }
      }
      return type;
    }
  }
}
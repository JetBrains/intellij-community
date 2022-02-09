// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
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
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

public final class HighlightMethodUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();
  private static final Logger LOG = Logger.getInstance(HighlightMethodUtil.class);

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

  static HighlightInfo checkMethodWeakerPrivileges(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                   @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                   boolean includeRealPositionInfo,
                                                   @NotNull PsiFile containingFile) {
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
      HighlightInfo info = isWeaker(method, modifierList, accessModifier, accessLevel, superMethod, includeRealPositionInfo);
      if (info != null) return info;
    }
    return null;
  }

  private static HighlightInfo isWeaker(@NotNull PsiMethod method,
                                        @NotNull PsiModifierList modifierList,
                                        @NotNull String accessModifier,
                                        int accessLevel,
                                        @NotNull PsiMethod superMethod,
                                        boolean includeRealPositionInfo) {
    int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
    if (accessLevel < superAccessLevel) {
      String description = JavaErrorBundle.message("weaker.privileges",
                                                   createClashMethodMessage(method, superMethod, true),
                                                   VisibilityUtil.toPresentableText(accessModifier),
                                                   PsiUtil.getAccessModifier(superAccessLevel));
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
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, null, QUICK_FIX_FACTORY.createChangeModifierFix());
      return info;
    }

    return null;
  }


  static HighlightInfo checkMethodIncompatibleReturnType(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                         @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                         boolean includeRealPositionInfo) {
    return checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, includeRealPositionInfo, null);
  }

  static HighlightInfo checkMethodIncompatibleReturnType(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                         @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                         boolean includeRealPositionInfo,
                                                         @Nullable TextRange textRange) {
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
      HighlightInfo info = checkSuperMethodSignature(
        superMethod, superMethodSignature, superReturnType, method, methodSignature, returnType,
        JavaErrorBundle.message("incompatible.return.type"), textRange, PsiUtil.getLanguageLevel(aClass));
      if (info != null) return info;
    }

    return null;
  }

  private static HighlightInfo checkSuperMethodSignature(@NotNull PsiMethod superMethod,
                                                         @NotNull MethodSignatureBackedByPsiMethod superMethodSignature,
                                                         @NotNull PsiType superReturnType,
                                                         @NotNull PsiMethod method,
                                                         @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                         @NotNull PsiType returnType,
                                                         @NotNull @Nls String detailMessage,
                                                         @NotNull TextRange range,
                                                         @NotNull LanguageLevel languageLevel) {
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
    boolean isJdk15 = languageLevel.isAtLeast(LanguageLevel.JDK_1_5);
    if (isJdk15 && !superMethodSignature.isRaw() && superMethodSignature.equals(methodSignature)) { //see 8.4.5
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
      if (isJdk15 && LambdaUtil.performWithSubstitutedParameterBounds(methodSignature.getTypeParameters(),
                                                                      methodSignature.getSubstitutor(),
                                                                      () -> TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType))) {
        return null;
      }
    }

    return createIncompatibleReturnTypeMessage(method, superMethod, substitutedSuperReturnType, returnType, detailMessage, range);
  }

  private static HighlightInfo createIncompatibleReturnTypeMessage(@NotNull PsiMethod method,
                                                                   @NotNull PsiMethod superMethod,
                                                                   @NotNull PsiType substitutedSuperReturnType,
                                                                   @NotNull PsiType returnType,
                                                                   @NotNull @Nls String detailMessage,
                                                                   @NotNull TextRange textRange) {
    String description = MessageFormat.format("{0}; {1}", createClashMethodMessage(method, superMethod, true), detailMessage);
    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(
      description).create();
    if (method instanceof LightRecordMethod) {
      for (IntentionAction fix : 
        HighlightFixUtil.getChangeVariableTypeFixes(((LightRecordMethod)method).getRecordComponent(), substitutedSuperReturnType)) {
        QuickFixAction.registerQuickFixAction(errorResult, fix);
      }
    }
    else {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMethodReturnFix(method, substitutedSuperReturnType, false));
    }
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createSuperMethodReturnFix(superMethod, returnType));
    PsiClass returnClass = PsiUtil.resolveClassInClassTypeOnly(returnType);
    if (returnClass != null && substitutedSuperReturnType instanceof PsiClassType) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createChangeParameterClassFix(returnClass, (PsiClassType)substitutedSuperReturnType));
    }

    return errorResult;
  }


  static HighlightInfo checkMethodOverridesFinal(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                 @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures) {
    PsiMethod method = methodSignature.getMethod();
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      HighlightInfo info = checkSuperMethodIsFinal(method, superMethod);
      if (info != null) return info;
    }
    return null;
  }

  private static HighlightInfo checkSuperMethodIsFinal(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    // strange things happen when super method is from Object and method from interface
    if (superMethod.hasModifierProperty(PsiModifier.FINAL)) {
      PsiClass superClass = superMethod.getContainingClass();
      String description = JavaErrorBundle.message("final.method.override",
                                                   JavaHighlightUtil.formatMethod(method),
                                                   JavaHighlightUtil.formatMethod(superMethod),
                                                     superClass != null ? HighlightUtil.formatClass(superClass) : "<unknown>");
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(superMethod, MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false)));
      return errorResult;
    }
    return null;
  }

  static HighlightInfo checkMethodIncompatibleThrows(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                     @NotNull List<? extends HierarchicalMethodSignature> superMethodSignatures,
                                                     boolean includeRealPositionInfo,
                                                     @NotNull PsiClass analyzedClass) {
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
        TextRange textRange;
        if (includeRealPositionInfo) {
          PsiElement exceptionContext = exceptionContexts.get(index);
          textRange = exceptionContext.getTextRange();
        }
        else {
          textRange = TextRange.EMPTY_RANGE;
        }
        HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(errorResult, new LocalQuickFixOnPsiElementAsIntentionAdapter(QUICK_FIX_FACTORY.createMethodThrowsFix(method, exception, false, false)));
        QuickFixAction.registerQuickFixAction(errorResult, new LocalQuickFixOnPsiElementAsIntentionAdapter(QUICK_FIX_FACTORY.createMethodThrowsFix(superMethod, exception, true, true)));
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
                                        PsiType exception,
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
                              HighlightInfoHolder holder) {
    PsiExpressionList list = methodCall.getArgumentList();
    PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
    JavaResolveResult[] results = referenceToMethod.multiResolve(true);
    JavaResolveResult resolveResult = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    PsiElement resolved = resolveResult.getElement();

    boolean isDummy = isDummyConstructorCall(methodCall, resolveHelper, list, referenceToMethod);
    if (isDummy) return;
    HighlightInfo highlightInfo;

    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (resolved instanceof PsiMethod && resolveResult.isValidResult()) {
      highlightInfo = HighlightUtil.checkUnhandledExceptions(methodCall);

      if (highlightInfo == null && ((PsiMethod)resolved).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = ((PsiMethod)resolved).getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          PsiElement element = ObjectUtils.notNull(referenceToMethod.getReferenceNameElement(), referenceToMethod);
          highlightInfo = HighlightUtil.checkFeature(element, HighlightingFeature.STATIC_INTERFACE_CALLS, languageLevel, file);
          if (highlightInfo == null) {
            highlightInfo = checkStaticInterfaceCallQualifier(referenceToMethod, resolveResult, methodCall, containingClass);
          }
        }
      }

      if (highlightInfo == null) {
        highlightInfo = GenericsHighlightUtil.checkInferredIntersections(substitutor, methodCall);
      }

      if (highlightInfo == null) {
        highlightInfo = checkVarargParameterErasureToBeAccessible((MethodCandidateInfo)resolveResult, methodCall);
      }

      if (highlightInfo == null) {
        highlightInfo = createIncompatibleTypeHighlightInfo(methodCall, resolveHelper, (MethodCandidateInfo)resolveResult, methodCall);
      }
    }
    else {
      MethodCandidateInfo candidateInfo = resolveResult instanceof MethodCandidateInfo ? (MethodCandidateInfo)resolveResult : null;
      PsiMethod resolvedMethod = candidateInfo != null ? candidateInfo.getElement() : null;

      if (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect()) {
        highlightInfo = null;
      }
      else if (candidateInfo != null && !candidateInfo.isApplicable()) {
        if (candidateInfo.isTypeArgumentsApplicable()) {
          highlightInfo = createIncompatibleCallHighlightInfo(holder, list, candidateInfo);

          if (highlightInfo != null) {
            registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
            registerMethodReturnFixAction(highlightInfo, candidateInfo, methodCall);
            registerTargetTypeFixesBasedOnApplicabilityInference(methodCall, candidateInfo, resolvedMethod, highlightInfo);
            holder.add(highlightInfo);
            return;
          }
        }
        else {
          PsiReferenceParameterList typeArgumentList = methodCall.getTypeArgumentList();
          PsiSubstitutor applicabilitySubstitutor = candidateInfo.getSubstitutor(false);
          if (typeArgumentList.getTypeArguments().length == 0 && resolvedMethod.hasTypeParameters()) {
            highlightInfo = GenericsHighlightUtil.checkInferredTypeArguments(resolvedMethod, methodCall, applicabilitySubstitutor);
          }
          else {
            highlightInfo = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, applicabilitySubstitutor, javaSdkVersion);
          }
        }
      }
      else {
        String description = JavaErrorBundle.message("method.call.expected");
        highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(description).create();
        if (resolved instanceof PsiClass) {
          QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createInsertNewFix(methodCall, (PsiClass)resolved));
        }
        else {
          TextRange range = getFixRange(methodCall);
          registerStaticMethodQualifierFixes(methodCall, highlightInfo);
          registerUsageFixes(methodCall, highlightInfo, range);
          if (resolved instanceof PsiVariable && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(((PsiVariable)resolved).getType());
            if (method != null) {
              QuickFixAction.registerQuickFixAction(highlightInfo, range, QUICK_FIX_FACTORY.createInsertMethodCallFix(methodCall, method));
            }
          }
        }
      }
    }
    if (highlightInfo == null) {
      highlightInfo = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, substitutor, javaSdkVersion);
    }
    holder.add(highlightInfo);
  }

  private static void registerStaticMethodQualifierFixes(@NotNull PsiMethodCallExpression methodCall, HighlightInfo highlightInfo) {
    TextRange methodExpressionRange = methodCall.getMethodExpression().getTextRange();
    QuickFixAction.registerQuickFixAction(highlightInfo, methodExpressionRange, QUICK_FIX_FACTORY.createStaticImportMethodFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, methodExpressionRange, QUICK_FIX_FACTORY.createQualifyStaticMethodCallFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, methodExpressionRange, QUICK_FIX_FACTORY.addMethodQualifierFix(methodCall));
  }

  /**
   * collect highlightInfos per each wrong argument; fixes would be set for the first one with fixRange: methodCall
   * @return highlight info for the first wrong arg expression
   */
  private static HighlightInfo createIncompatibleCallHighlightInfo(HighlightInfoHolder holder,
                                                                   PsiExpressionList list,
                                                                   @NotNull MethodCandidateInfo candidateInfo) {
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
      if (mismatchedExpressions.size() == 1) {
        toolTip = createOneArgMismatchTooltip(candidateInfo, mismatchedExpressions, expressions, parameters);
      }
      if (toolTip == null) {
        toolTip = createMismatchedArgumentsHtmlTooltip(candidateInfo, list);
      }
    }
    else {
      mismatchedExpressions = Collections.emptyList();
      toolTip = description;
    }

    if (mismatchedExpressions.size() == list.getExpressions().length || mismatchedExpressions.isEmpty()) {
      if (list.getTextRange().isEmpty()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(ObjectUtils.notNull(list.getPrevSibling(), list))
          .description(description)
          .escapedToolTip(toolTip).create();
      }
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).description(description).escapedToolTip(toolTip).navigationShift(1).create();
    }
    else {
      HighlightInfo highlightInfo = null;
      for (PsiExpression wrongArg : mismatchedExpressions) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(wrongArg)
          .description(description)
          .escapedToolTip(toolTip).create();
        if (highlightInfo == null) {
          highlightInfo = info;
        }
        else {
          holder.add(info);
        }
      }
      return highlightInfo;
    }
  }

  private static @NlsContexts.Tooltip String createOneArgMismatchTooltip(MethodCandidateInfo candidateInfo,
                                                                         @NotNull List<? extends PsiExpression> mismatchedExpressions,
                                                                         PsiExpression[] expressions,
                                                                         PsiParameter[] parameters) {
    PsiExpression wrongArg = mismatchedExpressions.get(0);
    PsiType argType = wrongArg.getType();
    if (argType != null) {
      if ((parameters.length == 0 || !parameters[parameters.length - 1].isVarArgs()) && parameters.length != expressions.length) {
        return createMismatchedArgumentCountTooltip(parameters, expressions);
      }
      int idx = ArrayUtil.find(expressions, wrongArg);
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

  static HighlightInfo createIncompatibleTypeHighlightInfo(@NotNull PsiCall methodCall,
                                                           @NotNull PsiResolveHelper resolveHelper,
                                                           @NotNull MethodCandidateInfo resolveResult,
                                                           @NotNull PsiElement elementToHighlight) {
    String errorMessage = resolveResult.getInferenceErrorMessage();
    if (errorMessage == null) return null;
    PsiMethod method = resolveResult.getElement();
    HighlightInfo highlightInfo;
    PsiType expectedTypeByParent = InferenceSession.getTargetTypeByParent(methodCall);
    PsiType actualType = resolveResult.getSubstitutor(false).substitute(method.getReturnType());
    TextRange fixRange = getFixRange(elementToHighlight);
    if (expectedTypeByParent != null && actualType != null && !expectedTypeByParent.isAssignableFrom(actualType)) {
      highlightInfo = HighlightUtil
        .createIncompatibleTypeHighlightInfo(expectedTypeByParent, actualType, fixRange, 0, XmlStringUtil.escapeString(errorMessage));
    }
    else {
      highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(errorMessage).range(fixRange).create();
    }
    if (highlightInfo != null && methodCall instanceof PsiMethodCallExpression) {
      registerMethodCallIntentions(highlightInfo, (PsiMethodCallExpression)methodCall, ((PsiMethodCallExpression)methodCall).getArgumentList(), resolveHelper);
      registerMethodReturnFixAction(highlightInfo, resolveResult, methodCall);
      registerTargetTypeFixesBasedOnApplicabilityInference((PsiMethodCallExpression)methodCall, resolveResult, method, highlightInfo);
    }
    return highlightInfo;
  }

  private static void registerUsageFixes(@NotNull PsiMethodCallExpression methodCall,
                                         @Nullable HighlightInfo highlightInfo,
                                         @NotNull TextRange range) {
    for (IntentionAction action : QUICK_FIX_FACTORY.createCreateMethodFromUsageFixes(methodCall)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, range, action);
    }
  }

  private static void registerThisSuperFixes(@NotNull PsiMethodCallExpression methodCall,
                                             @Nullable HighlightInfo highlightInfo,
                                             @NotNull TextRange range) {
    for (IntentionAction action : QUICK_FIX_FACTORY.createCreateConstructorFromCallExpressionFixes(methodCall)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, range, action);
    }
  }

  private static void registerTargetTypeFixesBasedOnApplicabilityInference(@NotNull PsiMethodCallExpression methodCall,
                                                                           @NotNull MethodCandidateInfo resolveResult,
                                                                           @NotNull PsiMethod resolved,
                                                                           HighlightInfo highlightInfo) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
    PsiVariable variable = null;
    if (parent instanceof PsiVariable) {
      variable = (PsiVariable)parent;
    }
    else if (parent instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      if (lExpression instanceof PsiReferenceExpression) {
        PsiElement resolve = ((PsiReferenceExpression)lExpression).resolve();
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

  static HighlightInfo checkStaticInterfaceCallQualifier(@NotNull PsiJavaCodeReferenceElement referenceToMethod,
                                                         @NotNull JavaResolveResult resolveResult,
                                                         @NotNull PsiElement elementToHighlight,
                                                         @NotNull PsiClass containingClass) {
    String message = checkStaticInterfaceMethodCallQualifier(referenceToMethod, resolveResult.getCurrentFileResolveScope(), containingClass);
    if (message != null) {
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message)
        .range(getFixRange(elementToHighlight)).create();
      if (referenceToMethod instanceof PsiReferenceExpression) {
        QuickFixAction
          .registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAccessStaticViaInstanceFix((PsiReferenceExpression)referenceToMethod, resolveResult));
      }
      return highlightInfo;
    }
    return null;
  }

  /* see also PsiReferenceExpressionImpl.hasValidQualifier() */
  private static @NlsContexts.DetailedDescription String checkStaticInterfaceMethodCallQualifier(@NotNull PsiJavaCodeReferenceElement ref,
                                                                                                 @Nullable PsiElement scope,
                                                                                                 @NotNull PsiClass containingClass) {
    @Nullable PsiElement qualifierExpression = ref.getQualifier();
    if (qualifierExpression == null && PsiTreeUtil.isAncestor(containingClass, ref, true)) {
      return null;
    }

    PsiElement resolve = null;
    if (qualifierExpression == null && scope instanceof PsiImportStaticStatement) {
      resolve = ((PsiImportStaticStatement)scope).resolveTargetClass();
    }
    else if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
      resolve = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
    }

    if (containingClass.getManager().areElementsEquivalent(resolve, containingClass)) {
      return null;
    }

    if (resolve instanceof PsiTypeParameter) {
      Set<PsiClass> classes = new HashSet<>();
      for (PsiClassType type : ((PsiTypeParameter)resolve).getExtendsListTypes()) {
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

  private static void registerMethodReturnFixAction(@NotNull HighlightInfo highlightInfo,
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
          QuickFixAction.registerQuickFixAction(highlightInfo,
                                                getFixRange(methodCall),
                                                QUICK_FIX_FACTORY.createMethodReturnFix(containerMethod, methodCallTypeByArgs, true));
        }
      }
    }
  }

  @NotNull
  private static List<PsiExpression> mismatchedArgs(PsiExpression @NotNull [] expressions,
                                                    PsiSubstitutor substitutor,
                                                    PsiParameter @NotNull [] parameters,
                                                    boolean varargs) {
    if ((parameters.length == 0 || !parameters[parameters.length - 1].isVarArgs()) && parameters.length > expressions.length) {
      return Collections.emptyList();
    }

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

  static HighlightInfo checkAmbiguousMethodCallIdentifier(@NotNull PsiReferenceExpression referenceToMethod,
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
      if (element instanceof PsiMethod && ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = ((PsiMethod)element).getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          HighlightInfo info = HighlightUtil.checkFeature(elementToHighlight, HighlightingFeature.STATIC_INTERFACE_CALLS, languageLevel, file);
          if (info != null) return info;
          info = checkStaticInterfaceCallQualifier(referenceToMethod, resolveResult, elementToHighlight, containingClass);
          if (info != null) return info;
        }
      }

      description = HighlightUtil.staticContextProblemDescription(element);
    }
    else if (candidates.length == 0) {
      PsiClass qualifierClass = RefactoringChangeUtil.getQualifierClass(referenceToMethod);
      String qualifier = qualifierClass != null ? qualifierClass.getName() : null;

      description = qualifier != null ? JavaErrorBundle
        .message("ambiguous.method.call.no.match", referenceToMethod.getReferenceName(), qualifier)
                                      : JavaErrorBundle
                      .message("cannot.resolve.method", referenceToMethod.getReferenceName() + buildArgTypesList(list, true));
      highlightInfoType = HighlightInfoType.WRONG_REF;
    }
    else {
      return null;
    }

    String toolTip = XmlStringUtil.escapeString(description);
    HighlightInfo info =
      HighlightInfo.newHighlightInfo(highlightInfoType).range(elementToHighlight).description(description).escapedToolTip(toolTip).create();
    if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      HighlightFixUtil.registerStaticProblemQuickFixAction(element, info, referenceToMethod);
    }
    registerMethodCallIntentions(info, methodCall, list, resolveHelper);

    TextRange fixRange = getFixRange(elementToHighlight);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, methodCall, info, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), info, fixRange);
    PermuteArgumentsFix.registerFix(info, methodCall, candidates, fixRange);
    registerChangeParameterClassFix(methodCall, list, info, fixRange);
    if (candidates.length == 0 && info != null) {
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(methodCall.getMethodExpression(), new QuickFixActionRegistrarImpl(info));
    }
    return info;
  }

  static HighlightInfo checkAmbiguousMethodCallArguments(@NotNull PsiReferenceExpression referenceToMethod,
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
    HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
    if (methodCandidate2 != null) {
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
      if (element != null && !resolveResult.isAccessible()) {
        return null;
      }
      if (element != null && !resolveResult.isStaticsScopeCorrect()) {
        return null;
      }
      String methodName = referenceToMethod.getReferenceName() + buildArgTypesList(list, true);
      description = JavaErrorBundle.message("cannot.resolve.method", methodName);
      if (candidates.length == 0) {
        return null;
      }
      toolTip = XmlStringUtil.escapeString(description);
    }
    TextRange fixRange = getFixRange(elementToHighlight);
    HighlightInfo info =
      HighlightInfo.newHighlightInfo(highlightInfoType).range(elementToHighlight).description(description).escapedToolTip(toolTip).create();
    if (!resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect() && methodCandidate2 != null) {
      HighlightFixUtil.registerAccessQuickFixAction((PsiJvmMember)element, referenceToMethod, info, resolveResult.getCurrentFileResolveScope(), fixRange);
    }
    if (methodCandidate2 == null) {
      registerMethodCallIntentions(info, methodCall, list, resolveHelper);
    }
    if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      HighlightFixUtil.registerStaticProblemQuickFixAction(element, info, referenceToMethod);
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, methodCall, info, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), info, fixRange);
    PermuteArgumentsFix.registerFix(info, methodCall, candidates, fixRange);
    registerChangeParameterClassFix(methodCall, list, info, fixRange);
    return info;
  }

  @NotNull
  private static Pair<MethodCandidateInfo, MethodCandidateInfo> findCandidates(JavaResolveResult @NotNull [] resolveResults) {
    MethodCandidateInfo methodCandidate1 = null;
    MethodCandidateInfo methodCandidate2 = null;
    for (JavaResolveResult result : resolveResults) {
      if (!(result instanceof MethodCandidateInfo)) continue;
      MethodCandidateInfo candidate = (MethodCandidateInfo)result;
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
      if (!(result instanceof MethodCandidateInfo)) continue;
      MethodCandidateInfo candidate = (MethodCandidateInfo)result;
      if (candidate.isAccessible()) candidateList.add(candidate);
    }
    return candidateList.toArray(new MethodCandidateInfo[0]);
  }

  private static void registerMethodCallIntentions(@Nullable HighlightInfo highlightInfo,
                                                   @NotNull PsiMethodCallExpression methodCall,
                                                   @NotNull PsiExpressionList list,
                                                   @NotNull PsiResolveHelper resolveHelper) {
    TextRange fixRange = getFixRange(methodCall);
    PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression) {
      PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (resolve instanceof PsiClass &&
          ((PsiClass)resolve).getContainingClass() != null &&
          !((PsiClass)resolve).hasModifierProperty(PsiModifier.STATIC)) {
        QuickFixAction.registerQuickFixActions(highlightInfo, fixRange, JvmElementActionFactories.createModifierActions((PsiClass)resolve, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, true)));
      }
    }
    else if (qualifierExpression instanceof PsiSuperExpression && ((PsiSuperExpression)qualifierExpression).getQualifier() == null) {
      QualifySuperArgumentFix.registerQuickFixAction((PsiSuperExpression)qualifierExpression, highlightInfo);
    }

    PsiType expectedTypeByParent = PsiTypesUtil.getExpectedTypeByParent(methodCall);
    if (expectedTypeByParent != null) {
      PsiType methodCallType = methodCall.getType();
      if (methodCallType != null &&
          TypeConversionUtil.areTypesConvertible(methodCallType, expectedTypeByParent) &&
          !TypeConversionUtil.isAssignable(expectedTypeByParent, methodCallType)) {
        QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createAddTypeCastFix(expectedTypeByParent, methodCall));
      }
    }

    CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates(methodCall, false);
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createSurroundWithArrayFix(methodCall, null));

    CastMethodArgumentFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    ConvertDoubleToFloatFix.registerIntentions(methodCandidates, list, highlightInfo, fixRange);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);

    CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(candidates, methodCall, highlightInfo, fixRange);

    WrapWithAdapterMethodCallFix.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createReplaceAddAllArrayToCollectionFix(methodCall));
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    MethodReturnFixFactory.INSTANCE.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    WrapExpressionFix.registerWrapAction(methodCandidates, list.getExpressions(), highlightInfo, fixRange);
    QualifyThisArgumentFix.registerQuickFixAction(methodCandidates, methodCall, highlightInfo, fixRange);
    registerMethodAccessLevelIntentions(methodCandidates, methodCall, list, highlightInfo, fixRange);

    if (!PermuteArgumentsFix.registerFix(highlightInfo, methodCall, methodCandidates, fixRange) &&
        !MoveParenthesisFix.registerFix(highlightInfo, methodCall, methodCandidates, fixRange)) {
      registerChangeMethodSignatureFromUsageIntentions(methodCandidates, list, highlightInfo, fixRange);
    }

    for (IntentionAction action : QUICK_FIX_FACTORY.getVariableTypeFromCallFixes(methodCall, list)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, action);
    }

    if (methodCandidates.length == 0) {
      registerStaticMethodQualifierFixes(methodCall, highlightInfo);
    }

    registerThisSuperFixes(methodCall, highlightInfo, fixRange);
    registerUsageFixes(methodCall, highlightInfo, fixRange);

    RemoveRedundantArgumentsFix.registerIntentions(methodCandidates, list, highlightInfo, fixRange);
    registerChangeParameterClassFix(methodCall, list, highlightInfo, fixRange);
  }

  private static void registerMethodAccessLevelIntentions(CandidateInfo @NotNull [] methodCandidates,
                                                          @NotNull PsiMethodCallExpression methodCall,
                                                          @NotNull PsiExpressionList exprList,
                                                          @Nullable HighlightInfo highlightInfo,
                                                          TextRange fixRange) {
    for (CandidateInfo methodCandidate : methodCandidates) {
      PsiMethod method = (PsiMethod)methodCandidate.getElement();
      if (!methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        HighlightFixUtil.registerAccessQuickFixAction(method, methodCall.getMethodExpression(), highlightInfo, methodCandidate.getCurrentFileResolveScope(),
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
      return createMismatchedArgumentCountTooltip(parameters, expressions);
    }

    HtmlBuilder message = new HtmlBuilder();
    message.append(getTypeMismatchTable(info, substitutor, parameters, expressions));

    String errorMessage = info != null ? info.getInferenceErrorMessage() : null;
    message.append(getTypeMismatchErrorHtml(errorMessage));
    return message.wrapWithHtmlBody().toString();
  }

  @NotNull
  private static @NlsContexts.Tooltip String createMismatchedArgumentCountTooltip(PsiParameter @NotNull [] parameters, PsiExpression[] expressions) {
    return HtmlChunk.text(JavaAnalysisBundle.message("arguments.count.mismatch", parameters.length, expressions.length))
      .wrapWith("html").toString();
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
    return paramType != null && TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression);
  }


  static HighlightInfo checkMethodMustHaveBody(@NotNull PsiMethod method, @Nullable PsiClass aClass) {
    HighlightInfo errorResult = null;
    if (method.getBody() == null
        && !method.hasModifierProperty(PsiModifier.ABSTRACT)
        && !method.hasModifierProperty(PsiModifier.NATIVE)
        && aClass != null
        && !aClass.isInterface()
        && !PsiUtilCore.hasErrorElementChild(method)) {
      int start = method.getModifierList().getTextRange().getStartOffset();
      int end = method.getTextRange().getEndOffset();

      String description = JavaErrorBundle.message("missing.method.body");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddMethodBodyFix(method));
      if (HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, method.getModifierList()) == null && !(aClass instanceof PsiAnonymousClass)) {
        QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(method, MemberRequestsKt.modifierRequest(JvmModifier.ABSTRACT, true)));
      }
    }
    return errorResult;
  }


  static HighlightInfo checkAbstractMethodInConcreteClass(@NotNull PsiMethod method, @NotNull PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    PsiClass aClass = method.getContainingClass();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)
        && aClass != null
        && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)
        && !aClass.isEnum()
        && !PsiUtilCore.hasErrorElementChild(method)) {
      String description = JavaErrorBundle.message("abstract.method.in.non.abstract.class");
      errorResult =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      if (method.getBody() != null) {
        QuickFixAction.registerQuickFixAction(errorResult,
                                              QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.ABSTRACT, false, false));
      }
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddMethodBodyFix(method));
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.ABSTRACT, true, false));
    }
    return errorResult;
  }

  static HighlightInfo checkConstructorName(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass != null) {
      String className = aClass instanceof PsiAnonymousClass ? null : aClass.getName();
      if (className == null || !Comparing.strEqual(method.getName(), className)) {
        PsiElement element = ObjectUtils.notNull(method.getNameIdentifier(), method);
        String description = JavaErrorBundle.message("missing.return.type");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
        if (className != null) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRenameElementFix(method, className));
        }
        return info;
      }
    }

    return null;
  }

  static HighlightInfo checkDuplicateMethod(@NotNull PsiClass aClass,
                                            @NotNull PsiMethod method,
                                            @NotNull MostlySingularMultiMap<MethodSignature, PsiMethod> duplicateMethods) {
    if (method instanceof ExternallyDefinedPsiElement) return null;
    MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    int methodCount = 1;
    List<PsiMethod> methods = (List<PsiMethod>)duplicateMethods.get(methodSignature);
    if (methods.size() > 1) {
      methodCount++;
    }

    if (methodCount == 1 && aClass.isEnum() &&
        GenericsHighlightUtil.isEnumSyntheticMethod(methodSignature, aClass.getProject())) {
      methodCount++;
    }
    if (methodCount > 1) {
      String description = JavaErrorBundle.message("duplicate.method",
                                                   JavaHighlightUtil.formatMethod(method),
                                                   HighlightUtil.formatClass(aClass));
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).
        range(method, textRange.getStartOffset(), textRange.getEndOffset()).
        descriptionAndTooltip(description).create();
    }
    return null;
  }

  static HighlightInfo checkMethodCanHaveBody(@NotNull PsiMethod method, @NotNull LanguageLevel languageLevel) {
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
        if (isStatic && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
          description = JavaErrorBundle.message("static.methods.in.interfaces.should.have.body");
        }
        else if (isPrivate && languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
          description = JavaErrorBundle.message("private.methods.in.interfaces.should.have.body");
        }
      }
      if (description != null) {
        additionalFixes.add(QUICK_FIX_FACTORY.createAddMethodBodyFix(method));
      }
    }
    else if (isInterface) {
      if (!isExtension && !isStatic && !isPrivate && !isConstructor) {
        description = JavaErrorBundle.message("interface.methods.cannot.have.body");
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
          if (Stream.of(method.findDeepestSuperMethods())
            .map(PsiMethod::getContainingClass)
            .filter(Objects::nonNull)
            .map(PsiClass::getQualifiedName)
            .noneMatch(CommonClassNames.JAVA_LANG_OBJECT::equals)) {
            IntentionAction makeDefaultFix = QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.DEFAULT, true, false);
            additionalFixes.add(PriorityIntentionActionWrapper.highPriority(makeDefaultFix));
            additionalFixes.add(QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, true, false));
          }
        }
      }
    }
    else if (isExtension) {
      description = JavaErrorBundle.message("extension.method.in.class");
      additionalFixes.add(QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.DEFAULT, false, false));
    }
    else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      description = JavaErrorBundle.message("abstract.methods.cannot.have.a.body");
    }
    else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      description = JavaErrorBundle.message("native.methods.cannot.have.a.body");
    }
    if (description == null) return null;

    TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) && !isInterface) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.ABSTRACT, false, false));
    }
    for (IntentionAction intentionAction : additionalFixes) {
      QuickFixAction.registerQuickFixAction(info, intentionAction);
    }
    if (!hasNoBody) {
      if (!isExtension) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteMethodBodyFix(method));
      }
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createPushDownMethodFix());
    }
    return info;
  }

  static HighlightInfo checkConstructorCallProblems(@NotNull PsiMethodCallExpression methodCall) {
    if (!JavaPsiConstructorUtil.isConstructorCall(methodCall)) return null;
    PsiElement codeBlock = methodCall.getParent().getParent();
    if (codeBlock instanceof PsiCodeBlock) {
      PsiMethod ctor = tryCast(codeBlock.getParent(), PsiMethod.class);
      if (ctor != null && ctor.isConstructor()) {
        if (JavaPsiRecordUtil.isCompactConstructor(ctor) ||
            JavaPsiRecordUtil.isExplicitCanonicalConstructor(ctor)) {
          String message = JavaErrorBundle.message("record.constructor.call.in.canonical");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(message).create();
        }
        PsiElement prevSibling = methodCall.getParent().getPrevSibling();
        while (true) {
          if (prevSibling == null) return null;
          if (prevSibling instanceof PsiStatement) break;
          prevSibling = prevSibling.getPrevSibling();
        }
      }
    }
    PsiReferenceExpression expression = methodCall.getMethodExpression();
    String message = JavaErrorBundle.message("constructor.call.must.be.first.statement", expression.getText() + "()");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(message).create();
  }


  static HighlightInfo checkSuperAbstractMethodDirectCall(@NotNull PsiMethodCallExpression methodCallExpression) {
    PsiReferenceExpression expression = methodCallExpression.getMethodExpression();
    if (!(expression.getQualifierExpression() instanceof PsiSuperExpression)) return null;
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method != null && method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String message = JavaErrorBundle.message("direct.abstract.method.access", JavaHighlightUtil.formatMethod(method));
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCallExpression).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(methodCallExpression));
      int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
      String name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, 0);
      String modifierText = VisibilityUtil.toPresentableText(PsiModifier.ABSTRACT);
      String text = QuickFixBundle.message("remove.modifier.fix", name, modifierText);
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddMethodBodyFix(method, text));
      return info;
    }
    return null;
  }

  static HighlightInfo checkConstructorCallsBaseClassConstructor(@NotNull PsiMethod constructor,
                                                                 @Nullable RefCountHolder refCountHolder,
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
    HighlightInfo info = HighlightClassUtil.checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, handledExceptions);
    if (info != null) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createInsertSuperFix(constructor));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createInsertThisFix(constructor));
      PsiClass superClass = aClass.getSuperClass();
      if (superClass != null) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddDefaultConstructorFix(superClass));
      }
    }
    return info;
  }


  /**
   * @return error if static method overrides instance method or
   *         instance method overrides static. see JLS 8.4.6.1, 8.4.6.2
   */
  static HighlightInfo checkStaticMethodOverride(@NotNull PsiMethod method, @NotNull PsiFile containingFile) {
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
      HighlightInfo highlightInfo = checkStaticMethodOverride(aClass, method, isStatic, superClass, superMethod, containingFile);
      if (highlightInfo != null) {
        return highlightInfo;
      }
    }

    return null;
  }

  private static HighlightInfo checkStaticMethodOverride(@NotNull PsiClass aClass,
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

      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      if (!isSuperMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, modifierList) == null) {
        QuickFixAction.registerQuickFixAction(info,
                                              QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, isSuperMethodStatic, false));
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
      HighlightInfo info = isWeaker(method, modifierList, accessModifier, accessLevel, superMethod, true);
      if (info != null) return info;
      info = checkSuperMethodIsFinal(method, superMethod);
      if (info != null) return info;
    }
    return null;
  }

  private static HighlightInfo checkInterfaceInheritedMethodsReturnTypes(@NotNull List<? extends MethodSignatureBackedByPsiMethod> superMethodSignatures,
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
      HighlightInfo info =
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
          return createIncompatibleReturnTypeMessage(otherSuperMethod, currentMethod, curType, otherReturnType,
                                                     JavaErrorBundle.message("unrelated.overriding.methods.return.types"), TextRange.EMPTY_RANGE);
        });
      if (info != null) return info;
    }
    return null;
  }

  static HighlightInfo checkOverrideEquivalentInheritedMethods(@NotNull PsiClass aClass,
                                                               @NotNull PsiFile containingFile,
                                                               @NotNull LanguageLevel languageLevel) {
    String description = null;
    boolean appendImplementMethodFix = true;
    Collection<HierarchicalMethodSignature> visibleSignatures = aClass.getVisibleSignatures();
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();

    Ultimate:
    for (HierarchicalMethodSignature signature : visibleSignatures) {
      PsiMethod method = signature.getMethod();
      if (!resolveHelper.isAccessible(method, aClass, null)) continue;
      List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

      boolean allAbstracts = method.hasModifierProperty(PsiModifier.ABSTRACT);
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || aClass.equals(containingClass)) continue; //to be checked at method level

      if (aClass.isInterface() && !containingClass.isInterface()) continue;
      HighlightInfo highlightInfo;
      if (allAbstracts) {
        superSignatures = new ArrayList<>(superSignatures);
        superSignatures.add(0, signature);
        highlightInfo = checkInterfaceInheritedMethodsReturnTypes(superSignatures, languageLevel);
      }
      else {
        highlightInfo = checkMethodIncompatibleReturnType(signature, superSignatures, false);
      }
      if (highlightInfo != null) description = highlightInfo.getDescription();

      if (method.hasModifierProperty(PsiModifier.STATIC)) {
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
        highlightInfo = checkMethodIncompatibleThrows(signature, superSignatures, false, aClass);
        if (highlightInfo != null) description = highlightInfo.getDescription();
      }

      if (description == null) {
        highlightInfo = checkMethodWeakerPrivileges(signature, superSignatures, false, containingFile);
        if (highlightInfo != null) description = highlightInfo.getDescription();
      }

      if (description != null) break;
    }


    if (description != null) {
      // show error info at the class level
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      if (appendImplementMethodFix) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createImplementMethodsFix(aClass));
      }
      return highlightInfo;
    }
    return null;
  }


  static HighlightInfo checkConstructorHandleSuperClassExceptions(@NotNull PsiMethod method) {
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
    HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    for (PsiClassType exception : unhandled) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new LocalQuickFixOnPsiElementAsIntentionAdapter(QUICK_FIX_FACTORY.createMethodThrowsFix(method, exception, true, false)));
    }
    return highlightInfo;
  }


  static HighlightInfo checkRecursiveConstructorInvocation(@NotNull PsiMethod method) {
    if (HighlightControlFlowUtil.isRecursivelyCalledConstructor(method)) {
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      String description = JavaErrorBundle.message("recursive.constructor.invocation");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    }
    return null;
  }

  @NotNull
  public static TextRange getFixRange(@NotNull PsiElement element) {
    TextRange range = element.getTextRange();
    int start = range.getStartOffset();
    int end = range.getEndOffset();

    PsiElement nextSibling = element.getNextSibling();
    if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.SEMICOLON)) {
      return new TextRange(start, end + 1);
    }
    return range;
  }


  static void checkNewExpression(@NotNull PsiNewExpression expression,
                                 @Nullable PsiType type,
                                 @NotNull HighlightInfoHolder holder,
                                 @NotNull JavaSdkVersion javaSdkVersion) {
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

    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    checkConstructorCall(typeResult, expression, type, classReference, holder, javaSdkVersion, expression.getArgumentList());
  }

  static void checkAmbiguousConstructorCall(PsiJavaCodeReferenceElement ref,
                                            PsiElement resolved,
                                            PsiElement parent, 
                                            HighlightInfoHolder holder, 
                                            JavaSdkVersion version) {
    if (resolved instanceof PsiClass && parent instanceof PsiNewExpression && ((PsiClass)resolved).getConstructors().length > 0) {
      PsiNewExpression newExpression = (PsiNewExpression)parent;
      if (newExpression.resolveMethod() == null && !PsiTreeUtil.findChildrenOfType(newExpression.getArgumentList(), PsiFunctionalExpression.class).isEmpty()) {
        PsiType type = newExpression.getType();
        if (type != null) {
          checkConstructorCall(((PsiClassType)type).resolveGenerics(), newExpression, type, newExpression.getClassReference(), holder, version, ref);
        }
      }
    }
  }

  static void checkConstructorCall(@NotNull PsiClassType.ClassResolveResult typeResolveResult,
                                   @NotNull PsiConstructorCall constructorCall,
                                   @NotNull PsiType type,
                                   @Nullable PsiJavaCodeReferenceElement classReference,
                                   @NotNull HighlightInfoHolder holder,
                                   @NotNull JavaSdkVersion javaSdkVersion, 
                                   @Nullable PsiElement elementToHighlight) {
    if (elementToHighlight == null) return;
    PsiExpressionList list = constructorCall.getArgumentList();
    if (list == null) return;
    PsiClass aClass = typeResolveResult.getElement();
    if (aClass == null) return;
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(holder.getProject()).getResolveHelper();
    PsiClass accessObjectClass = null;
    if (constructorCall instanceof PsiNewExpression) {
      PsiExpression qualifier = ((PsiNewExpression)constructorCall).getQualifier();
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }
    }
    if (classReference != null && !resolveHelper.isAccessible(aClass, constructorCall, accessObjectClass)) {
      String description = HighlightUtil.accessProblemDescription(classReference, aClass, typeResolveResult);
      PsiElement element = ObjectUtils.notNull(classReference.getReferenceNameElement(), classReference);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      HighlightFixUtil.registerAccessQuickFixAction(aClass, classReference, info, null, null);
      holder.add(info);
      return;
    }
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) {
      if (!list.isEmpty()) {
        String constructorName = aClass.getName();
        String argTypes = buildArgTypesList(list, false);
        String description = JavaErrorBundle.message("wrong.constructor.arguments", constructorName + "()", argTypes);
        String tooltip = createMismatchedArgumentsHtmlTooltip(list, null, PsiParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).description(description).escapedToolTip(tooltip).navigationShift(+1).create();
        if (classReference != null && info != null) {
          ConstructorParametersFixer.registerFixActions(classReference, constructorCall, info, getFixRange(list));
        }
        QuickFixAction.registerQuickFixActions(
          info, constructorCall.getTextRange(), QUICK_FIX_FACTORY.createCreateConstructorFromUsageFixes(constructorCall)
        );
        holder.add(info);
        return;
      }
      if (classReference != null && aClass.hasModifierProperty(PsiModifier.PROTECTED) && callingProtectedConstructorFromDerivedClass(constructorCall, aClass)) {
        holder.add(buildAccessProblem(classReference, aClass, typeResolveResult));
      } else if (aClass.isInterface() && constructorCall instanceof PsiNewExpression) {
        PsiReferenceParameterList typeArgumentList = ((PsiNewExpression)constructorCall).getTypeArgumentList();
        if (typeArgumentList.getTypeArguments().length > 0) {
          holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeArgumentList)
            .descriptionAndTooltip(JavaErrorBundle.message("anonymous.class.implements.interface.cannot.have.type.arguments")).create());
        }
      }
    }
    else {
      PsiElement place = list;
      if (constructorCall instanceof PsiNewExpression) {
        PsiAnonymousClass anonymousClass = ((PsiNewExpression)constructorCall).getAnonymousClass();
        if (anonymousClass != null) place = anonymousClass;
      }

      JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, place);
      MethodCandidateInfo result = null;
      if (results.length == 1) result = (MethodCandidateInfo)results[0];

      PsiMethod constructor = result == null ? null : result.getElement();

      boolean applicable = true;
      try {
        PsiDiamondType diamondType = constructorCall instanceof PsiNewExpression ? PsiDiamondType.getDiamondType((PsiNewExpression)constructorCall) : null;
        JavaResolveResult staticFactory = diamondType != null ? diamondType.getStaticFactory() : null;
        if (staticFactory instanceof MethodCandidateInfo) {
          if (((MethodCandidateInfo)staticFactory).isApplicable()) {
            result = (MethodCandidateInfo)staticFactory;
            if (constructor == null) {
              constructor = ((MethodCandidateInfo)staticFactory).getElement();
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

      if (constructor == null) {
        String name = aClass.getName();
        name += buildArgTypesList(list, true);
        String description = JavaErrorBundle.message("cannot.resolve.constructor", name);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(elementToHighlight).descriptionAndTooltip(description).create();
        if (info != null) {
          TextRange fixRange = getFixRange(elementToHighlight);
          WrapExpressionFix.registerWrapAction(results, list.getExpressions(), info, fixRange);
          registerFixesOnInvalidConstructorCall(constructorCall, classReference, list, aClass, constructors, results, info, fixRange);
          holder.add(info);
        }
      }
      else if (classReference != null &&
               (!result.isAccessible() ||
                constructor.hasModifierProperty(PsiModifier.PROTECTED) && callingProtectedConstructorFromDerivedClass(constructorCall, aClass))) {
        holder.add(buildAccessProblem(classReference, constructor, result));
      }
      else if (!applicable) {
        HighlightInfo info = createIncompatibleCallHighlightInfo(holder, list, result);
        if (info != null) {
          JavaResolveResult[] methodCandidates = results;
          if (constructorCall instanceof PsiNewExpression) {
            methodCandidates = resolveHelper.getReferencedMethodCandidates((PsiCallExpression)constructorCall, true);
          }
          registerFixesOnInvalidConstructorCall(constructorCall, classReference, list, aClass, constructors, methodCandidates, info, getFixRange(list));
          registerMethodReturnFixAction(info, result, constructorCall);
          holder.add(info);
        }
      }
      else if (constructorCall instanceof PsiNewExpression) {
        PsiReferenceParameterList typeArgumentList = ((PsiNewExpression)constructorCall).getTypeArgumentList();
        HighlightInfo info = GenericsHighlightUtil.checkReferenceTypeArgumentList(constructor, typeArgumentList, result.getSubstitutor(), false, javaSdkVersion);
        if (info != null) {
          holder.add(info);
        }
      }

      if (result != null && !holder.hasErrorResults()) {
        holder.add(checkVarargParameterErasureToBeAccessible(result, constructorCall));
      }
      if (result != null && !holder.hasErrorResults()) {
        holder.add(createIncompatibleTypeHighlightInfo(constructorCall, resolveHelper, result, constructorCall));
      }
    }
  }

  /**
   * If the compile-time declaration is applicable by variable arity invocation,
   * then where the last formal parameter type of the invocation type of the method is Fn[],
   * it is a compile-time error if the type which is the erasure of Fn is not accessible at the point of invocation.
   */
  private static HighlightInfo checkVarargParameterErasureToBeAccessible(@NotNull MethodCandidateInfo info, @NotNull PsiCall place) {
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
          .range(argumentList != null ? argumentList : place)
          .create();
      }
    }
    return null;
  }

  private static void registerFixesOnInvalidConstructorCall(@NotNull PsiConstructorCall constructorCall,
                                                            @Nullable PsiJavaCodeReferenceElement classReference,
                                                            @NotNull PsiExpressionList list,
                                                            @NotNull PsiClass aClass,
                                                            PsiMethod @NotNull [] constructors,
                                                            JavaResolveResult @NotNull [] results,
                                                            @NotNull HighlightInfo info,
                                                            TextRange fixRange) {
    if (classReference != null) {
      ConstructorParametersFixer.registerFixActions(classReference, constructorCall, info, fixRange);
      ChangeTypeArgumentsFix.registerIntentions(results, list, info, aClass, fixRange);
      ConvertDoubleToFloatFix.registerIntentions(results, list, info, fixRange);
    }
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(constructors, constructorCall, info, fixRange);
    QuickFixAction.registerQuickFixAction(info, fixRange, QUICK_FIX_FACTORY.createSurroundWithArrayFix(constructorCall, null));
    if (!PermuteArgumentsFix.registerFix(info, constructorCall, toMethodCandidates(results), fixRange)) {
      registerChangeMethodSignatureFromUsageIntentions(results, list, info, fixRange);
    }
    QuickFixAction.registerQuickFixActions(
      info, constructorCall.getTextRange(), QUICK_FIX_FACTORY.createCreateConstructorFromUsageFixes(constructorCall)
    );
    registerChangeParameterClassFix(constructorCall, list, info, fixRange);
  }

  private static HighlightInfo buildAccessProblem(@NotNull PsiJavaCodeReferenceElement ref,
                                                  @NotNull PsiJvmMember resolved,
                                                  @NotNull JavaResolveResult result) {
    String description = HighlightUtil.accessProblemDescription(ref, resolved, result);
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description).navigationShift(+1).create();
    if (result.isStaticsScopeCorrect()) {
      HighlightFixUtil.registerAccessQuickFixAction(resolved, ref, info, result.getCurrentFileResolveScope(), null);
    }
    return info;
  }

  private static boolean callingProtectedConstructorFromDerivedClass(@NotNull PsiConstructorCall place, @NotNull PsiClass constructorClass) {
    // indirect instantiation via anonymous class is ok
    if (place instanceof PsiNewExpression && ((PsiNewExpression)place).getAnonymousClass() != null) return false;
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
                                                      @Nullable HighlightInfo highlightInfo, TextRange fixRange) {
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
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createChangeParameterClassFix(expressionClass, (PsiClassType)parameterType));
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntentions(JavaResolveResult @NotNull [] candidates,
                                                                       @NotNull PsiExpressionList list,
                                                                       @Nullable HighlightInfo highlightInfo,
                                                                       @Nullable TextRange fixRange) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerChangeMethodSignatureFromUsageIntention(expressions, highlightInfo, fixRange, candidate, list);
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntention(PsiExpression @NotNull [] expressions,
                                                                      @Nullable HighlightInfo highlightInfo,
                                                                      @Nullable TextRange fixRange,
                                                                      @NotNull JavaResolveResult candidate,
                                                                      @NotNull PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method != null && context.getManager().isInProject(method)) {
      IntentionAction fix = QUICK_FIX_FACTORY.createChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context, false, 2);
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, fix);
      IntentionAction f2 = QUICK_FIX_FACTORY.createChangeMethodSignatureFromUsageReverseOrderFix(method, expressions, substitutor, context, false, 2);
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, f2);
    }
  }

  static PsiType determineReturnType(@NotNull PsiMethod method) {
    PsiManager manager = method.getManager();
    PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(method);
    if (returnStatements.length == 0) return PsiType.VOID;
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
    if (currentType == null || PsiType.VOID.equals(currentType)) return valueType;
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

    return Objects.requireNonNull(GenericsUtil.getLeastUpperBound(currentType, valueType, manager));
  }

  static HighlightInfo checkRecordAccessorDeclaration(PsiMethod method) {
    PsiRecordComponent component = JavaPsiRecordUtil.getRecordComponentForAccessor(method);
    if (component == null) return null;
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return null;
    PsiType componentType = component.getType();
    PsiType methodType = method.getReturnType();
    if (methodType == null) return null; // Either constructor or incorrect method, will be reported in another way
    if (componentType instanceof PsiEllipsisType) {
      componentType = ((PsiEllipsisType)componentType).getComponentType().createArrayType();
    }
    if (!componentType.equals(methodType)) {
      String message =
        JavaErrorBundle.message("record.accessor.wrong.return.type", componentType.getPresentableText(), methodType.getPresentableText());
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
        Objects.requireNonNull(method.getReturnTypeElement())).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMethodReturnFix(method, componentType, false));
      return info;
    }
    return checkRecordSpecialMethodDeclaration(method, JavaErrorBundle.message("record.accessor"));
  }

  @NotNull
  static List<HighlightInfo> checkRecordConstructorDeclaration(@NotNull PsiMethod method) {
    if (!method.isConstructor()) return Collections.emptyList();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return Collections.emptyList();
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return Collections.emptyList();
    if (!aClass.isRecord()) {
      if (JavaPsiRecordUtil.isCompactConstructor(method)) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
          identifier).descriptionAndTooltip(JavaErrorBundle.message("compact.constructor.in.regular.class")).create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddParameterListFix(method));
        return Collections.singletonList(info);
      }
      return Collections.emptyList();
    }
    if (JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiRecordComponent[] components = aClass.getRecordComponents();
      List<HighlightInfo> problems = new ArrayList<>();
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
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
            Objects.requireNonNull(parameters[i].getTypeElement())).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMethodParameterTypeFix(method, i, componentType, false));
          problems.add(info);
        }
        if (componentName != null && !parameterName.equals(componentName)) {
          String message = JavaErrorBundle.message("record.canonical.constructor.wrong.parameter.name", componentName, parameterName);
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(
            Objects.requireNonNull(parameters[i].getNameIdentifier())).descriptionAndTooltip(message).create();
          if (!ContainerUtil.exists(parameters, parameter -> parameter.getName().equals(componentName))) {
            QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRenameElementFix(parameters[i], componentName));
          }
          problems.add(info);
        }
      }
      ContainerUtil
        .addIfNotNull(problems, checkRecordSpecialMethodDeclaration(method, JavaErrorBundle.message("record.canonical.constructor")));
      return problems;
    } else if (JavaPsiRecordUtil.isCompactConstructor(method)) {
      return Collections
        .singletonList(checkRecordSpecialMethodDeclaration(method, JavaErrorBundle.message("record.compact.constructor")));
    }
    else {
      // Non-canonical constructor
      PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
      if (call == null || JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
        String message = JavaErrorBundle.message("record.no.constructor.call.in.non.canonical");
        return Collections.singletonList(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
                                           .descriptionAndTooltip(message).create());
      }
      return Collections.emptyList();
    }
  }

  @Nullable
  private static HighlightInfo checkRecordSpecialMethodDeclaration(PsiMethod method, String methodTitle) {
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return null;
    PsiTypeParameterList typeParameterList = method.getTypeParameterList();
    if (typeParameterList != null && typeParameterList.getTypeParameters().length > 0) {
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeParameterList)
        .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.type.parameters", methodTitle))
        .create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(typeParameterList.getTypeParameters()));
      return info;
    }
    if (method.isConstructor()) {
      AccessModifier modifier = AccessModifier.fromModifierList(method.getModifierList());
      PsiModifierList classModifierList = Objects.requireNonNull(method.getContainingClass()).getModifierList();
      if (classModifierList != null) {
        AccessModifier classModifier = AccessModifier.fromModifierList(classModifierList);
        if (classModifier.isWeaker(modifier)) {
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
            .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.stronger.access", methodTitle, classModifier))
            .create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(
            method, classModifier.toPsiModifier(), true, false));
          return info;
        }
      }
    } else if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
        .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.non.public", methodTitle))
        .create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.PUBLIC, true, false));
      return info;
    }
    PsiReferenceList throwsList = method.getThrowsList();
    if (throwsList.getReferenceElements().length > 0) {
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(throwsList)
        .descriptionAndTooltip(JavaErrorBundle.message("record.special.method.throws", methodTitle))
        .create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(throwsList.getReferenceElements()));
      return info;
    }
    return null;
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
      if (value == null) return new ReturnModel(statement, PsiType.VOID);
      if (ExpressionUtils.nonStructuralChildren(value).anyMatch(c -> c instanceof PsiFunctionalExpression)) return null;
      PsiType type = RefactoringChangeUtil.getTypeByExpression(value);
      if (type == null || type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) return null;
      return new ReturnModel(statement, type, getLeastValueType(value, type));
    }

    @NotNull
    private static PsiType getLeastValueType(@NotNull PsiExpression returnValue, @NotNull PsiType type) {
      if (type instanceof PsiPrimitiveType) {
        int rank = TypeConversionUtil.getTypeRank(type);
        if (rank < TypeConversionUtil.BYTE_RANK || rank > TypeConversionUtil.INT_RANK) return type;
        PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(returnValue.getProject()).getConstantEvaluationHelper();
        Object res = evaluator.computeConstantExpression(returnValue);
        if (res instanceof Number) {
          long value = ((Number)res).longValue();
          if (-128 <= value && value <= 127) return PsiType.BYTE;
          if (-32768 <= value && value <= 32767) return PsiType.SHORT;
          if (0 <= value && value <= 0xFFFF) return PsiType.CHAR;
        }
      }
      return type;
    }
  }
}
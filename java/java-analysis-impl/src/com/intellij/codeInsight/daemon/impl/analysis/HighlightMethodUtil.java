// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.codeInspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.asConsumer;

public final class HighlightMethodUtil {

  private HighlightMethodUtil() { }

  static @NotNull @NlsContexts.DetailedDescription String createClashMethodMessage(@NotNull PsiMethod method1, @NotNull PsiMethod method2, boolean showContainingClasses) {
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


  static HighlightInfo.Builder checkMethodIncompatibleReturnType(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
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
      HighlightInfo.Builder info = checkSuperMethodSignature(
        superMethod, superMethodSignature, superReturnType, method, methodSignature, returnType,
        textRange, PsiUtil.getLanguageLevel(aClass));
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

    return createIncompatibleReturnTypeMessage(method, superMethod, substitutedSuperReturnType, returnType,
                                               JavaErrorBundle.message("incompatible.return.type"), range
    );
  }

  private static HighlightInfo.@NotNull Builder createIncompatibleReturnTypeMessage(@NotNull PsiMethod method,
                                                                                    @NotNull PsiMethod superMethod,
                                                                                    @NotNull PsiType substitutedSuperReturnType,
                                                                                    @NotNull PsiType returnType,
                                                                                    @NotNull @Nls String detailMessage,
                                                                                    @NotNull TextRange textRange) {
    String description = MessageFormat.format("{0}; {1}", createClashMethodMessage(method, superMethod, true), detailMessage);
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

  static HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiCall methodCall,
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
        AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(asConsumer(builder),
                                                             (PsiExpression)methodCall, expectedTypeByParent, actualType);
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
      HighlightFixUtil.registerMethodCallIntentions(asConsumer(builder), callExpression, callExpression.getArgumentList());
      if (!PsiTypesUtil.mentionsTypeParameters(actualType, Set.of(method.getTypeParameters()))) {
        HighlightFixUtil.registerMethodReturnFixAction(asConsumer(builder), resolveResult, methodCall);
      }
      HighlightFixUtil.registerTargetTypeFixesBasedOnApplicabilityInference(callExpression, resolveResult, method, asConsumer(builder));
    }
    return builder;
  }

  private static boolean favorParentReport(@NotNull PsiCall methodCall, @NotNull String errorMessage) {
    // Parent resolve failed as well, and it's likely more informative.
    // Suppress this error to allow reporting from parent
    return (errorMessage.equals(JavaPsiBundle.message("error.incompatible.type.failed.to.resolve.argument")) ||
            errorMessage.equals(JavaPsiBundle.message("error.incompatible.type.declaration.for.the.method.reference.not.found"))) &&
           hasSurroundingInferenceError(methodCall);
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
                                                                  @NotNull LanguageLevel languageLevel,
                                                                  @NotNull PsiFile file) {
    MethodCandidateInfo methodCandidate2 = findCandidates(resolveResults).second;
    if (methodCandidate2 != null) return null;
    MethodCandidateInfo[] candidates = HighlightFixUtil.toMethodCandidates(resolveResults);

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
               !primitiveType.equals(PsiTypes.nullType())) {
        if (PsiTypes.voidType().equals(primitiveType) &&
            PsiUtil.deparenthesizeExpression(qualifierExpression) instanceof PsiReferenceExpression) {
          return null;
        }
        description = JavaErrorBundle.message("cannot.call.method.on.type", primitiveType.getPresentableText(false));
      }
      else {
        if (qualifierExpression != null) {
          PsiType type = qualifierExpression.getType();
          if (type instanceof PsiClassType t && t.resolve() == null || PsiTypes.nullType().equals(type)) {
            return null;
          }
        }
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
    HighlightFixUtil.registerMethodCallIntentions(asConsumer(builder), methodCall, list);

    TextRange fixRange = getFixRange(elementToHighlight);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, asConsumer(builder));
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, methodCall, asConsumer(builder));
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, asConsumer(builder));
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), asConsumer(builder));
    PermuteArgumentsFix.registerFix(asConsumer(builder), methodCall, candidates);
    var action = RemoveRepeatingCallFix.createFix(methodCall);
    if (action != null) {
      builder.registerFix(action, null, null, fixRange, null);
    }
    HighlightFixUtil.registerChangeParameterClassFix(methodCall, list, asConsumer(builder));
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
                                                                 @NotNull PsiElement elementToHighlight) {
    Pair<MethodCandidateInfo, MethodCandidateInfo> pair = findCandidates(resolveResults);
    MethodCandidateInfo methodCandidate1 = pair.first;
    MethodCandidateInfo methodCandidate2 = pair.second;
    MethodCandidateInfo[] candidates = HighlightFixUtil.toMethodCandidates(resolveResults);

    String description;
    String toolTip;
    PsiExpression[] expressions = list.getExpressions();
    if (methodCandidate2 != null) {
      if (IncompleteModelUtil.isIncompleteModel(list) &&
          ContainerUtil.exists(expressions, e -> IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(e))) {
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
          ContainerUtil.exists(expressions, IncompleteModelUtil::mayHaveUnknownTypeDueToPendingReference)) {
        return null;
      }
      if (ContainerUtil.exists(expressions, e -> e.getType() == null)) {
        return null;
      }
      String methodName = referenceToMethod.getReferenceName() + buildArgTypesList(list, true);
      description = JavaErrorBundle.message("cannot.resolve.method", methodName);
      toolTip = XmlStringUtil.escapeString(description);
    }
    if (PsiTreeUtil.hasErrorElements(list)) {
      return null;
    }
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).description(description).escapedToolTip(toolTip);
    if (!resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect() && methodCandidate2 != null) {
      HighlightFixUtil.registerAccessQuickFixAction(asConsumer(builder), (PsiJvmMember)element, referenceToMethod,
                                                    resolveResult.getCurrentFileResolveScope());
    }
    if (methodCandidate2 == null) {
      HighlightFixUtil.registerMethodCallIntentions(asConsumer(builder), methodCall, list);
    }
    if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      HighlightFixUtil.registerStaticProblemQuickFixAction(builder, element, referenceToMethod);
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, asConsumer(builder));
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, methodCall, asConsumer(builder));
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, asConsumer(builder));
    WrapExpressionFix.registerWrapAction(candidates, expressions, asConsumer(builder));
    PermuteArgumentsFix.registerFix(asConsumer(builder), methodCall, candidates);
    HighlightFixUtil.registerChangeParameterClassFix(methodCall, list, asConsumer(builder));
    return builder;
  }

  private static @NotNull Pair<MethodCandidateInfo, MethodCandidateInfo> findCandidates(JavaResolveResult @NotNull [] resolveResults) {
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

  private static @NotNull @NlsContexts.Tooltip String createAmbiguousMethodHtmlTooltip(MethodCandidateInfo @NotNull [] methodCandidates) {
    return JavaErrorBundle.message("ambiguous.method.html.tooltip",
                                     methodCandidates[0].getElement().getParameterList().getParametersCount() + 2,
                                   createAmbiguousMethodHtmlTooltipMethodRow(methodCandidates[0]),
                                   getContainingClassName(methodCandidates[0]),
                                   createAmbiguousMethodHtmlTooltipMethodRow(methodCandidates[1]),
                                   getContainingClassName(methodCandidates[1]));
  }

  private static @NotNull String getContainingClassName(@NotNull MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiClass containingClass = method.getContainingClass();
    return containingClass == null ? method.getContainingFile().getName() : HighlightUtil.formatClass(containingClass, false);
  }

  @Language("HTML")
  private static @NotNull String createAmbiguousMethodHtmlTooltipMethodRow(@NotNull MethodCandidateInfo methodCandidate) {
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
            // only report an abstract method in enum when there are no enum constants to implement it
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

  static HighlightInfo.Builder checkConstructorHandleSuperClassExceptions(@NotNull PsiMethod method) {
    if (!method.isConstructor()) {
      return null;
    }
    PsiCodeBlock body = method.getBody();
    PsiStatement[] statements = body == null ? null : body.getStatements();
    if (statements == null) return null;

    // if we have unhandled exception inside the method body, we could not have been called here,
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

  public static @NotNull TextRange getFixRange(@NotNull PsiElement element) {
    PsiElement nextSibling = element.getNextSibling();
    TextRange range = element.getTextRange();
    if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.SEMICOLON)) {
      return range.grown(1);
    }
    return range;
  }

  private static @NotNull String buildArgTypesList(@NotNull PsiExpressionList list, boolean shortNames) {
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

  private static @NotNull PsiType lub(@Nullable PsiType currentType,
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

  static boolean hasSurroundingInferenceError(@NotNull PsiElement context) {
    PsiCall topCall = LambdaUtil.treeWalkUp(context);
    if (topCall == null) return false;
    while (context != topCall) {
      context = context.getParent();
      if (context instanceof PsiMethodCallExpression call &&
          call.resolveMethodGenerics() instanceof MethodCandidateInfo info &&
          info.getInferenceErrorMessage() != null) {
        // Possibly inapplicable method reference due to the surrounding call inference failure:
        // suppress method reference error in order to display more relevant inference error.
        return true;
      }
    }
    return false;
  }

  static HighlightInfo.@Nullable Builder checkConstructorInImplicitClass(@NotNull PsiMethod method) {
    if (!method.isConstructor()) {
      return null;
    }
    if (!(method.getContainingClass() instanceof PsiImplicitClass)) {
      return null;
    }
    String description = JavaErrorBundle.message("implicit.class.with.explicit.constructor");
    TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
    HighlightInfo.Builder builder =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
    IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(method);
    builder.registerFix(action, null, null, null, null);
    return builder;
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

    private static @Nullable ReturnModel create(@NotNull PsiReturnStatement statement) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) return new ReturnModel(statement, PsiTypes.voidType());
      if (ExpressionUtils.nonStructuralChildren(value).anyMatch(c -> c instanceof PsiFunctionalExpression)) return null;
      PsiType type = RefactoringChangeUtil.getTypeByExpression(value);
      if (type == null || type instanceof PsiClassType classType && classType.resolve() == null) return null;
      return new ReturnModel(statement, type, getLeastValueType(value, type));
    }

    private static @NotNull PsiType getLeastValueType(@NotNull PsiExpression returnValue, @NotNull PsiType type) {
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
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
        HighlightFixUtil.registerChangeVariableTypeFixes(var, actualType, builder);
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
}
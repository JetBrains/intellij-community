/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixOnPsiElementAsIntentionAdapter;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Highlight method problems
 *
 * @author cdr
 * Date: Aug 14, 2002
 */
public class HighlightMethodUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();
  private static final String MISMATCH_COLOR = UIUtil.isUnderDarcula() ? "ff6464" : "red";

  private HighlightMethodUtil() { }

  static String createClashMethodMessage(PsiMethod method1, PsiMethod method2, boolean showContainingClasses) {
    @NonNls String pattern = showContainingClasses ? "clash.methods.message.show.classes" : "clash.methods.message";
    return JavaErrorMessages.message(pattern,
                                     JavaHighlightUtil.formatMethod(method1),
                                     JavaHighlightUtil.formatMethod(method2),
                                     HighlightUtil.formatClass(method1.getContainingClass()),
                                     HighlightUtil.formatClass(method2.getContainingClass()));
  }

  static HighlightInfo checkMethodWeakerPrivileges(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                   @NotNull List<HierarchicalMethodSignature> superMethodSignatures,
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

  private static HighlightInfo isWeaker(final PsiMethod method, final PsiModifierList modifierList, final String accessModifier, final int accessLevel,
                                        final PsiMethod superMethod,
                                        final boolean includeRealPositionInfo) {
    int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
    if (accessLevel < superAccessLevel) {
      String description = JavaErrorMessages.message("weaker.privileges",
                                                     createClashMethodMessage(method, superMethod, true),
                                                     VisibilityUtil.toPresentableText(accessModifier),
                                                     PsiUtil.getAccessModifier(superAccessLevel));
      TextRange textRange;
      if (includeRealPositionInfo) {
        PsiElement keyword = PsiUtil.findModifierInList(modifierList, accessModifier);
        if (keyword == null) {
          // in case of package-private or some crazy third-party plugin where some access modifier implied even if it's absent
          textRange = method.getNameIdentifier().getTextRange();
        }
        else {
          textRange = keyword.getTextRange();
        }
      }
      else {
        textRange = TextRange.EMPTY_RANGE;
      }
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo,
                                            QUICK_FIX_FACTORY.createModifierListFix(method, PsiUtil.getAccessModifier(superAccessLevel), true, false));
      return highlightInfo;
    }
    return null;
  }


  static HighlightInfo checkMethodIncompatibleReturnType(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                         @NotNull List<HierarchicalMethodSignature> superMethodSignatures,
                                                         boolean includeRealPositionInfo) {
    return checkMethodIncompatibleReturnType(methodSignature, superMethodSignatures, includeRealPositionInfo, null);
  }

  static HighlightInfo checkMethodIncompatibleReturnType(@NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                         @NotNull List<HierarchicalMethodSignature> superMethodSignatures,
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
      TextRange toHighlight = textRange != null ? textRange
                                          : includeRealPositionInfo ? method.getReturnTypeElement().getTextRange() : TextRange.EMPTY_RANGE;
      HighlightInfo highlightInfo = checkSuperMethodSignature(superMethod, superMethodSignature, superReturnType, method, methodSignature,
                                                              returnType, JavaErrorMessages.message("incompatible.return.type"),
                                                              toHighlight, PsiUtil.getLanguageLevel(aClass));
      if (highlightInfo != null) return highlightInfo;
    }

    return null;
  }

  private static HighlightInfo checkSuperMethodSignature(@NotNull PsiMethod superMethod,
                                                         @NotNull MethodSignatureBackedByPsiMethod superMethodSignature,
                                                         PsiType superReturnType,
                                                         @NotNull PsiMethod method,
                                                         @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                         @NotNull PsiType returnType,
                                                         @NotNull String detailMessage,
                                                         @NotNull TextRange range,
                                                         @NotNull LanguageLevel languageLevel) {
    if (superReturnType == null) return null;
    final PsiClass superContainingClass = superMethod.getContainingClass();
    if (superContainingClass != null && 
        CommonClassNames.JAVA_LANG_OBJECT.equals(superContainingClass.getQualifiedName()) &&
        !superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && !superContainingClass.isInterface()) {
        return null;
      }
    }

    PsiType substitutedSuperReturnType;
    final boolean isJdk15 = languageLevel.isAtLeast(LanguageLevel.JDK_1_5);
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
      if (isJdk15 && TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType)) {
        return null;
      }
    }

    return createIncompatibleReturnTypeMessage(method, superMethod, substitutedSuperReturnType, returnType, detailMessage, range);
  }

  private static HighlightInfo createIncompatibleReturnTypeMessage(@NotNull PsiMethod method,
                                                                   @NotNull PsiMethod superMethod,
                                                                   @NotNull PsiType substitutedSuperReturnType,
                                                                   @NotNull PsiType returnType,
                                                                   @NotNull String detailMessage,
                                                                   @NotNull TextRange textRange) {
    String description = MessageFormat.format("{0}; {1}", createClashMethodMessage(method, superMethod, true), detailMessage);
    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(
      description).create();
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMethodReturnFix(method, substitutedSuperReturnType, false));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createSuperMethodReturnFix(superMethod, returnType));

    return errorResult;
  }


  static HighlightInfo checkMethodOverridesFinal(MethodSignatureBackedByPsiMethod methodSignature,
                                                 List<HierarchicalMethodSignature> superMethodSignatures) {
    PsiMethod method = methodSignature.getMethod();
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      HighlightInfo info = checkSuperMethodIsFinal(method, superMethod);
      if (info != null) return info;
    }
    return null;
  }

  private static HighlightInfo checkSuperMethodIsFinal(PsiMethod method, PsiMethod superMethod) {
    // strange things happen when super method is from Object and method from interface
    if (superMethod.hasModifierProperty(PsiModifier.FINAL)) {
      String description = JavaErrorMessages.message("final.method.override",
                                                 JavaHighlightUtil.formatMethod(method),
                                                 JavaHighlightUtil.formatMethod(superMethod),
                                                 HighlightUtil.formatClass(superMethod.getContainingClass()));
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(errorResult,
                                            QUICK_FIX_FACTORY.createModifierListFix(superMethod, PsiModifier.FINAL, false, true));
      return errorResult;
    }
    return null;
  }

  static HighlightInfo checkMethodIncompatibleThrows(MethodSignatureBackedByPsiMethod methodSignature,
                                                            List<HierarchicalMethodSignature> superMethodSignatures,
                                                            boolean includeRealPositionInfo, PsiClass analyzedClass) {
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
      if (!ExceptionUtil.isUncheckedException(exception)) {
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
          final PsiClass superContainingClass = superMethod.getContainingClass();
          if (superContainingClass != null && !superContainingClass.isInterface()) continue;
          if (superContainingClass != null && !aClass.isInheritor(superContainingClass, true)) continue;
        }
        PsiClassType exception = checkedExceptions.get(index);
        String description = JavaErrorMessages.message("overridden.method.does.not.throw",
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
  private static int getExtraExceptionNum(final MethodSignature methodSignature,
                                          final MethodSignatureBackedByPsiMethod superSignature,
                                          List<PsiClassType> checkedExceptions, PsiSubstitutor substitutorForDerivedClass) {
    PsiMethod superMethod = superSignature.getMethod();
    PsiSubstitutor substitutorForMethod = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superSignature);
    for (int i = 0; i < checkedExceptions.size(); i++) {
      final PsiClassType checkedEx = checkedExceptions.get(i);
      final PsiType substituted = substitutorForMethod != null ? substitutorForMethod.substitute(checkedEx) : TypeConversionUtil.erasure(checkedEx);
      PsiType exception = substitutorForDerivedClass.substitute(substituted);
      if (!isMethodThrows(superMethod, substitutorForMethod, exception, substitutorForDerivedClass)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isMethodThrows(PsiMethod method, @Nullable PsiSubstitutor substitutorForMethod, PsiType exception, PsiSubstitutor substitutorForDerivedClass) {
    PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
    for (PsiClassType thrownException1 : thrownExceptions) {
      PsiType thrownException = substitutorForMethod != null ? substitutorForMethod.substitute(thrownException1) : TypeConversionUtil.erasure(thrownException1);
      thrownException = substitutorForDerivedClass.substitute(thrownException);
      if (TypeConversionUtil.isAssignable(thrownException, exception)) return true;
    }
    return false;
  }

  @Nullable
  static HighlightInfo checkMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                       @NotNull PsiResolveHelper resolveHelper,
                                       @NotNull LanguageLevel languageLevel,
                                       @NotNull JavaSdkVersion javaSdkVersion) {
    PsiExpressionList list = methodCall.getArgumentList();
    PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
    JavaResolveResult[] results = referenceToMethod.multiResolve(true);
    JavaResolveResult resolveResult = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    PsiElement resolved = resolveResult.getElement();

    boolean isDummy = isDummyConstructorCall(methodCall, resolveHelper, list, referenceToMethod);
    if (isDummy) return null;
    HighlightInfo highlightInfo;

    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (resolved instanceof PsiMethod && resolveResult.isValidResult()) {
      TextRange fixRange = getFixRange(methodCall);
      highlightInfo = HighlightUtil.checkUnhandledExceptions(methodCall, fixRange);
      if (highlightInfo == null) {
        final String invalidCallMessage = 
          LambdaUtil.getInvalidQualifier4StaticInterfaceMethodMessage((PsiMethod)resolved, methodCall.getMethodExpression(), resolveResult.getCurrentFileResolveScope(), languageLevel);
        if (invalidCallMessage != null) {
          highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(invalidCallMessage).range(fixRange).create();
          if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createIncreaseLanguageLevelFix(LanguageLevel.JDK_1_8));
          }
        } else {
          highlightInfo = GenericsHighlightUtil.checkInferredIntersections(substitutor, fixRange);
        }
        
        if (highlightInfo == null) {
          highlightInfo = checkVarargParameterErasureToBeAccessible((MethodCandidateInfo)resolveResult, methodCall);
        }

        if (highlightInfo == null) {
          final String errorMessage = ((MethodCandidateInfo)resolveResult).getInferenceErrorMessage();
          if (errorMessage != null) {
            highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(errorMessage).range(fixRange).create();
            if (highlightInfo != null) {
              registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
              registerMethodReturnFixAction(highlightInfo, (MethodCandidateInfo)resolveResult, methodCall);
            }
          }
        }
      }
    }
    else {
      PsiMethod resolvedMethod = null;
      MethodCandidateInfo candidateInfo = null;
      if (resolveResult instanceof MethodCandidateInfo) {
        candidateInfo = (MethodCandidateInfo)resolveResult;
        resolvedMethod = candidateInfo.getElement();
      }

      if (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect()) {
        highlightInfo = null;
      }
      else if (candidateInfo != null && !candidateInfo.isApplicable()) {
        if (candidateInfo.isTypeArgumentsApplicable()) {
          String methodName = HighlightMessageUtil.getSymbolName(resolved, substitutor);
          PsiElement parent = resolved.getParent();
          String containerName = parent == null ? "" : HighlightMessageUtil.getSymbolName(parent, substitutor);
          String argTypes = buildArgTypesList(list);
          String description = JavaErrorMessages.message("wrong.method.arguments", methodName, containerName, argTypes);
          final Ref<PsiElement> elementToHighlight = new Ref<>(list);
          String toolTip;
          if (parent instanceof PsiClass) {
            toolTip = buildOneLineMismatchDescription(list, candidateInfo, elementToHighlight);
            if (toolTip == null) {
              toolTip = createMismatchedArgumentsHtmlTooltip(candidateInfo, list);
            }
          }
          else {
            toolTip = description;
          }
          PsiElement element = elementToHighlight.get();
          int navigationShift = element instanceof PsiExpressionList ? +1 : 0; // argument list starts with paren which there is no need to highlight
          highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element)
            .description(description).escapedToolTip(toolTip).navigationShift(navigationShift).create();
          if (highlightInfo != null) {
            registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
            registerMethodReturnFixAction(highlightInfo, candidateInfo, methodCall);
          }
        }
        else {
          PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
          PsiReferenceParameterList typeArgumentList = methodCall.getTypeArgumentList();
          if (typeArgumentList.getTypeArguments().length == 0 && resolvedMethod.hasTypeParameters()) {
            highlightInfo = GenericsHighlightUtil.checkInferredTypeArguments(resolvedMethod, methodCall, substitutor);
          }
          else {
            highlightInfo = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, methodExpression, substitutor, javaSdkVersion);
          }
        }
      }
      else {
        String description = JavaErrorMessages.message("method.call.expected");
        highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(description).create();
        if (resolved instanceof PsiClass) {
          QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createInsertNewFix(methodCall, (PsiClass)resolved));
        }
        else {
          TextRange range = getFixRange(methodCall);
          QuickFixAction.registerQuickFixAction(highlightInfo, range, QUICK_FIX_FACTORY.createCreateMethodFromUsageFix(methodCall));
          QuickFixAction.registerQuickFixAction(highlightInfo, range, QUICK_FIX_FACTORY.createCreateAbstractMethodFromUsageFix(methodCall));
          QuickFixAction.registerQuickFixAction(highlightInfo, range, QUICK_FIX_FACTORY.createCreatePropertyFromUsageFix(methodCall));
        }
      }
    }
    if (highlightInfo == null) {
      highlightInfo = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, substitutor,
                                                                                     javaSdkVersion);
    }
    return highlightInfo;
  }

  private static void registerMethodReturnFixAction(HighlightInfo highlightInfo,
                                                    MethodCandidateInfo candidate,
                                                    PsiCall methodCall) {
    if (methodCall.getParent() instanceof PsiReturnStatement) {
      final PsiMethod containerMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiLambdaExpression.class);
      if (containerMethod != null) {
        final PsiMethod method = candidate.getElement();
        final PsiExpression methodCallCopy =
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

  private static String buildOneLineMismatchDescription(@NotNull PsiExpressionList list,
                                                        @NotNull MethodCandidateInfo candidateInfo,
                                                        @NotNull Ref<PsiElement> elementToHighlight) {
    final PsiExpression[] expressions = list.getExpressions();
    final PsiMethod resolvedMethod = candidateInfo.getElement();
    final PsiSubstitutor substitutor = candidateInfo.getSubstitutor();
    final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
    if (expressions.length == parameters.length && parameters.length > 1) {
      int idx = -1;
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        if (expression instanceof PsiMethodCallExpression) {
          final JavaResolveResult result = ((PsiCallExpression)expression).resolveMethodGenerics();
          if (result instanceof MethodCandidateInfo &&
              PsiUtil.isLanguageLevel8OrHigher(list) &&
              ((MethodCandidateInfo)result).isToInferApplicability() &&
              ((MethodCandidateInfo)result).getInferenceErrorMessage() == null) {
            continue;
          }
        }
        if (!TypeConversionUtil.areTypesAssignmentCompatible(substitutor.substitute(parameters[i].getType()), expression)) {
          if (idx != -1) {
            idx = -1;
            break;
          }
          else {
            idx = i;
          }
        }
      }

      if (idx > -1) {
        final PsiExpression wrongArg = expressions[idx];
        final PsiType argType = wrongArg.getType();
        if (argType != null) {
          elementToHighlight.set(wrongArg);
          final String message = JavaErrorMessages
            .message("incompatible.call.types", idx + 1, substitutor.substitute(parameters[idx].getType()).getCanonicalText(), argType.getCanonicalText());

          return XmlStringUtil.wrapInHtml("<body>" + XmlStringUtil.escapeString(message) +
                                          " <a href=\"#assignment/" + XmlStringUtil.escapeString(createMismatchedArgumentsHtmlTooltip(candidateInfo, list)) + "\"" +
                                          (UIUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "") +
                                          ">" + DaemonBundle.message("inspection.extended.description") + "</a></body>");
        }
      }
    }
    return null;
  }

  static boolean isDummyConstructorCall(PsiMethodCallExpression methodCall,
                                        PsiResolveHelper resolveHelper,
                                        PsiExpressionList list,
                                        PsiReferenceExpression referenceToMethod) {
    boolean isDummy = false;
    boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
    if (isThisOrSuper) {
      // super(..) or this(..)
      if (list.getExpressions().length == 0) { // implicit ctr call
        CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
        if (candidates.length == 1 && !candidates[0].getElement().isPhysical()) {
          isDummy = true;// dummy constructor
        }
      }
    }
    return isDummy;
  }

  @Nullable
  static HighlightInfo checkAmbiguousMethodCallIdentifier(@NotNull PsiReferenceExpression referenceToMethod,
                                                @NotNull JavaResolveResult[] resolveResults,
                                                @NotNull PsiExpressionList list,
                                                final PsiElement element,
                                                @NotNull JavaResolveResult resolveResult,
                                                @NotNull PsiMethodCallExpression methodCall,
                                                @NotNull PsiResolveHelper resolveHelper) {
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
    MethodCandidateInfo[] candidates = toMethodCandidates(resolveResults);

    HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
    if (methodCandidate2 != null) {
      return null;
    }
    String description;
    PsiElement elementToHighlight;
    if (element != null && !resolveResult.isAccessible()) {
      description = HighlightUtil.buildProblemWithAccessDescription(referenceToMethod, resolveResult);
      elementToHighlight = referenceToMethod.getReferenceNameElement();
    }
    else if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(referenceToMethod);
      final String staticInterfaceMethodMessage = 
        element instanceof PsiMethod 
        ? LambdaUtil.getInvalidQualifier4StaticInterfaceMethodMessage((PsiMethod)element, referenceToMethod, 
                                                                      resolveResult.getCurrentFileResolveScope(), languageLevel) 
        : null;
      description = staticInterfaceMethodMessage != null 
                    ? staticInterfaceMethodMessage 
                    : HighlightUtil.buildProblemWithStaticDescription(element);
      elementToHighlight = referenceToMethod.getReferenceNameElement();
    }
    else {
      String methodName = referenceToMethod.getReferenceName() + buildArgTypesList(list);
      description = JavaErrorMessages.message("cannot.resolve.method", methodName);
      if (candidates.length == 0) {
        elementToHighlight = referenceToMethod.getReferenceNameElement();
        highlightInfoType = HighlightInfoType.WRONG_REF;
      }
      else {
        return null;
      }
    }
    String toolTip = XmlStringUtil.escapeString(description);
    HighlightInfo info =
      HighlightInfo.newHighlightInfo(highlightInfoType).range(elementToHighlight).description(description).escapedToolTip(toolTip).create();
    registerMethodCallIntentions(info, methodCall, list, resolveHelper);
    if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      HighlightUtil.registerStaticProblemQuickFixAction(element, info, referenceToMethod);
    }

    TextRange fixRange = getFixRange(elementToHighlight);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapArrayToArraysAsListFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapLongWithMathToIntExactFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    PermuteArgumentsFix.registerFix(info, methodCall, candidates, fixRange);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), info);
    registerChangeParameterClassFix(methodCall, list, info);
    return info;
  }

  @Nullable
  static HighlightInfo checkAmbiguousMethodCallArguments(@NotNull PsiReferenceExpression referenceToMethod,
                                                         @NotNull JavaResolveResult[] resolveResults,
                                                         @NotNull PsiExpressionList list,
                                                         final PsiElement element,
                                                         @NotNull JavaResolveResult resolveResult,
                                                         @NotNull PsiMethodCallExpression methodCall,
                                                         @NotNull PsiResolveHelper resolveHelper, 
                                                         @NotNull PsiElement elementToHighlight) {
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
    MethodCandidateInfo[] candidates = toMethodCandidates(resolveResults);

    String description;
    String toolTip;
    HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
    if (methodCandidate2 != null) {
      PsiMethod element1 = methodCandidate1.getElement();
      String m1 = PsiFormatUtil.formatMethod(element1,
                                             methodCandidate1.getSubstitutor(),
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      PsiMethod element2 = methodCandidate2.getElement();
      String m2 = PsiFormatUtil.formatMethod(element2,
                                             methodCandidate2.getSubstitutor(),
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      VirtualFile virtualFile1 = PsiUtilCore.getVirtualFile(element1);
      VirtualFile virtualFile2 = PsiUtilCore.getVirtualFile(element2);
      if (!Comparing.equal(virtualFile1, virtualFile2)) {
        if (virtualFile1 != null) m1 += " (In " + virtualFile1.getPresentableUrl() + ")";
        if (virtualFile2 != null) m2 += " (In " + virtualFile2.getPresentableUrl() + ")";
      }
      description = JavaErrorMessages.message("ambiguous.method.call", m1, m2);
      toolTip = createAmbiguousMethodHtmlTooltip(new MethodCandidateInfo[]{methodCandidate1, methodCandidate2});
    }
    else {
      if (element != null && !resolveResult.isAccessible()) {
        return null;
      }
      if (element != null && !resolveResult.isStaticsScopeCorrect()) {
        return null;
      }
      String methodName = referenceToMethod.getReferenceName() + buildArgTypesList(list);
      description = JavaErrorMessages.message("cannot.resolve.method", methodName);
      if (candidates.length == 0) {
        return null;
      }
      toolTip = XmlStringUtil.escapeString(description);
    }
    HighlightInfo info =
      HighlightInfo.newHighlightInfo(highlightInfoType).range(elementToHighlight).description(description).escapedToolTip(toolTip).create();
    if (methodCandidate2 == null) {
      registerMethodCallIntentions(info, methodCall, list, resolveHelper);
    }
    if (!resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect() && methodCandidate2 != null) {
      HighlightUtil.registerAccessQuickFixAction((PsiMember)element, referenceToMethod, info, resolveResult.getCurrentFileResolveScope());
    }
    if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      HighlightUtil.registerStaticProblemQuickFixAction(element, info, referenceToMethod);
    }

    TextRange fixRange = getFixRange(elementToHighlight);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapArrayToArraysAsListFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapLongWithMathToIntExactFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, methodCall, info, fixRange);
    PermuteArgumentsFix.registerFix(info, methodCall, candidates, fixRange);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), info);
    registerChangeParameterClassFix(methodCall, list, info);
    return info;
  }

  @NotNull
  private static MethodCandidateInfo[] toMethodCandidates(@NotNull JavaResolveResult[] resolveResults) {
    List<MethodCandidateInfo> candidateList = new ArrayList<>(resolveResults.length);

    for (JavaResolveResult result : resolveResults) {
      if (!(result instanceof MethodCandidateInfo)) continue;
      MethodCandidateInfo candidate = (MethodCandidateInfo)result;
      if (candidate.isAccessible()) candidateList.add(candidate);
    }
    return candidateList.toArray(new MethodCandidateInfo[candidateList.size()]);
  }

  private static void registerMethodCallIntentions(@Nullable HighlightInfo highlightInfo,
                                                   PsiMethodCallExpression methodCall,
                                                   PsiExpressionList list,
                                                   PsiResolveHelper resolveHelper) {
    TextRange fixRange = getFixRange(methodCall);
    final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (resolve instanceof PsiClass &&
          ((PsiClass)resolve).getContainingClass() != null &&
          !((PsiClass)resolve).hasModifierProperty(PsiModifier.STATIC)) {
        QuickFixAction.registerQuickFixAction(highlightInfo,
                                              QUICK_FIX_FACTORY.createModifierListFix((PsiClass)resolve, PsiModifier.STATIC, true, false));
      }
    }

    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createCreateMethodFromUsageFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createCreateAbstractMethodFromUsageFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createCreateConstructorFromSuperFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createCreateConstructorFromThisFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createCreatePropertyFromUsageFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createCreateGetterSetterPropertyFromUsageFix(methodCall));
    CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates(methodCall, false);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    PermuteArgumentsFix.registerFix(highlightInfo, methodCall, methodCandidates, fixRange);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    WrapArrayToArraysAsListFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    WrapLongWithMathToIntExactFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    registerMethodAccessLevelIntentions(methodCandidates, methodCall, list, highlightInfo);
    registerChangeMethodSignatureFromUsageIntentions(methodCandidates, list, highlightInfo, fixRange);
    RemoveRedundantArgumentsFix.registerIntentions(methodCandidates, list, highlightInfo, fixRange);
    ConvertDoubleToFloatFix.registerIntentions(methodCandidates, list, highlightInfo, fixRange);
    WrapExpressionFix.registerWrapAction(methodCandidates, list.getExpressions(), highlightInfo);
    registerChangeParameterClassFix(methodCall, list, highlightInfo);
    if (methodCandidates.length == 0) {
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createStaticImportMethodFix(methodCall));
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.addMethodQualifierFix(methodCall));
    }
    for (IntentionAction action : QUICK_FIX_FACTORY.getVariableTypeFromCallFixes(methodCall, list)) {
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, action);
    }
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createReplaceAddAllArrayToCollectionFix(methodCall));
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, QUICK_FIX_FACTORY.createSurroundWithArrayFix(methodCall, null));
    QualifyThisArgumentFix.registerQuickFixAction(methodCandidates, methodCall, highlightInfo, fixRange);

    CandidateInfo[] candidates = resolveHelper.getReferencedMethodCandidates(methodCall, true);
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(candidates, methodCall, highlightInfo);
  }

  private static void registerMethodAccessLevelIntentions(CandidateInfo[] methodCandidates,
                                                          PsiMethodCallExpression methodCall,
                                                          PsiExpressionList exprList,
                                                          HighlightInfo highlightInfo) {
    for (CandidateInfo methodCandidate : methodCandidates) {
      PsiMethod method = (PsiMethod)methodCandidate.getElement();
      if (!methodCandidate.isAccessible() && PsiUtil.isApplicable(method, methodCandidate.getSubstitutor(), exprList)) {
        HighlightUtil.registerAccessQuickFixAction(method, methodCall.getMethodExpression(), highlightInfo, methodCandidate.getCurrentFileResolveScope());
      }
    }
  }

  @NotNull
  private static String createAmbiguousMethodHtmlTooltip(MethodCandidateInfo[] methodCandidates) {
    return JavaErrorMessages.message("ambiguous.method.html.tooltip",
                                     methodCandidates[0].getElement().getParameterList().getParametersCount() + 2,
                                     createAmbiguousMethodHtmlTooltipMethodRow(methodCandidates[0]),
                                     getContainingClassName(methodCandidates[0]),
                                     createAmbiguousMethodHtmlTooltipMethodRow(methodCandidates[1]),
                                     getContainingClassName(methodCandidates[1]));
  }

  private static String getContainingClassName(final MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiClass containingClass = method.getContainingClass();
    return containingClass == null ? method.getContainingFile().getName() : HighlightUtil.formatClass(containingClass, false);
  }

  @Language("HTML")
  private static String createAmbiguousMethodHtmlTooltipMethodRow(final MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
    @NonNls @Language("HTML") String ms = "<td><b>" + method.getName() + "</b></td>";

    for (int j = 0; j < parameters.length; j++) {
      PsiParameter parameter = parameters[j];
      PsiType type = substitutor.substitute(parameter.getType());
      ms += "<td><b>" + (j == 0 ? "(" : "") +
            XmlStringUtil.escapeString(type.getPresentableText())
            + (j == parameters.length - 1 ? ")" : ",") + "</b></td>";
    }
    if (parameters.length == 0) {
      ms += "<td><b>()</b></td>";
    }
    return ms;
  }

  private static String createMismatchedArgumentsHtmlTooltip(MethodCandidateInfo info, PsiExpressionList list) {
    PsiMethod method = info.getElement();
    PsiSubstitutor substitutor = info.getSubstitutor();
    PsiClass aClass = method.getContainingClass();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    String methodName = method.getName();
    return createMismatchedArgumentsHtmlTooltip(list, info, parameters, methodName, substitutor, aClass);
  }

  private static String createShortMismatchedArgumentsHtmlTooltip(PsiExpressionList list,
                                                                  @Nullable MethodCandidateInfo info, 
                                                                  PsiParameter[] parameters,
                                                                  String methodName,
                                                                  PsiSubstitutor substitutor,
                                                                  PsiClass aClass) {
    PsiExpression[] expressions = list.getExpressions();
    int cols = Math.max(parameters.length, expressions.length);

    @Language("HTML")
    @NonNls String parensizedName = methodName + (parameters.length == 0 ? "(&nbsp;)&nbsp;" : "");
    String errorMessage = info != null ? info.getParentInferenceErrorMessage(list) : null;
    return JavaErrorMessages.message(
      "argument.mismatch.html.tooltip",
      cols - parameters.length + 1,
      parensizedName,
      HighlightUtil.formatClass(aClass, false),
      createMismatchedArgsHtmlTooltipParamsRow(parameters, substitutor, expressions),
      createMismatchedArgsHtmlTooltipArgumentsRow(expressions, parameters, substitutor, cols),
      errorMessage != null ? "<br/>reason: " + XmlStringUtil.escapeString(errorMessage).replaceAll("\n", "<br/>") : ""
    );
  }

  private static String esctrim(@NotNull String s) {
    return XmlStringUtil.escapeString(trimNicely(s));
  }

  private static String trimNicely(String s) {
    if (s.length() <= 40) return s;

    List<TextRange> wordIndices = StringUtil.getWordIndicesIn(s);
    if (wordIndices.size() > 2) {
      int firstWordEnd = wordIndices.get(0).getEndOffset();

      // try firstWord...remainder
      for (int i = 1; i<wordIndices.size();i++) {
        int stringLength = firstWordEnd + s.length() - wordIndices.get(i).getStartOffset();
        if (stringLength <= 40) {
          return s.substring(0, firstWordEnd) + "..." + s.substring(wordIndices.get(i).getStartOffset());
        }
      }
    }
    // maybe one last word will fit?
    if (!wordIndices.isEmpty() && s.length() - wordIndices.get(wordIndices.size()-1).getStartOffset() <= 40) {
      return "..." + s.substring(wordIndices.get(wordIndices.size()-1).getStartOffset());
    }

    return StringUtil.last(s, 40, true).toString();
  }

  private static String createMismatchedArgumentsHtmlTooltip(PsiExpressionList list,
                                                             MethodCandidateInfo info, 
                                                             PsiParameter[] parameters,
                                                             String methodName,
                                                             PsiSubstitutor substitutor,
                                                             PsiClass aClass) {
    return Math.max(parameters.length, list.getExpressions().length) <= 2
           ? createShortMismatchedArgumentsHtmlTooltip(list, info, parameters, methodName, substitutor, aClass)
           : createLongMismatchedArgumentsHtmlTooltip(list, info, parameters, methodName, substitutor, aClass);
  }

  @SuppressWarnings("StringContatenationInLoop")
  @Language("HTML")
  private static String createLongMismatchedArgumentsHtmlTooltip(PsiExpressionList list,
                                                                 @Nullable MethodCandidateInfo info, 
                                                                 PsiParameter[] parameters,
                                                                 String methodName,
                                                                 PsiSubstitutor substitutor,
                                                                 PsiClass aClass) {
    PsiExpression[] expressions = list.getExpressions();

    @SuppressWarnings("NonConstantStringShouldBeStringBuffer") @NonNls
    String s = "<html><body><table border=0>" +
               "<tr><td colspan=3>" +
               "<nobr><b>" + methodName + "()</b> in <b>" + HighlightUtil.formatClass(aClass, false) +"</b> cannot be applied to:</nobr>" +
               "</td></tr>"+
               "<tr><td colspan=2 align=left>Expected<br>Parameters:</td><td align=left>Actual<br>Arguments:</td></tr>"+
               "<tr><td colspan=3><hr></td></tr>"
      ;

    for (int i = 0; i < Math.max(parameters.length,expressions.length); i++) {
      PsiParameter parameter = i < parameters.length ? parameters[i] : null;
      PsiExpression expression = i < expressions.length ? expressions[i] : null;
      boolean showShort = showShortType(i, parameters, expressions, substitutor);
      @NonNls String mismatchColor = showShort ? null : UIUtil.isUnderDarcula() ? "FF6B68" : "red";

      s += "<tr" + (i % 2 == 0 ? " style='background-color: #"
                                 + (UIUtil.isUnderDarcula() ? ColorUtil.toHex(ColorUtil.shift(UIUtil.getToolTipBackground(), 1.1)) : "eeeeee")
                                 + "'" : "") + ">";
      s += "<td><b><nobr>";
      if (parameter != null) {
        String name = parameter.getName();
        if (name != null) {
          s += esctrim(name) +":";
        }
      }
      s += "</nobr></b></td>";

      s += "<td><b><nobr>";
      if (parameter != null) {
        PsiType type = substitutor.substitute(parameter.getType());
        s +=  "<font " + (mismatchColor == null ? "" : "color=" + mismatchColor) + ">" +
              esctrim(showShort ? type.getPresentableText() : JavaHighlightUtil.formatType(type))
              + "</font>"
              ;
      }
      s += "</nobr></b></td>";

      s += "<td><b><nobr>";
      if (expression != null) {
        PsiType type = expression.getType();
        s += "<font " + (mismatchColor == null ? "" : "color='" + mismatchColor + "'") + ">" +
               esctrim(expression.getText()) + "&nbsp;&nbsp;"+
              (mismatchColor == null || type == null || type == PsiType.NULL ? "" : "("+esctrim(JavaHighlightUtil.formatType(type))+")")
              + "</font>"
              ;

      }
      s += "</nobr></b></td>";

      s += "</tr>";
    }

    s+= "</table>";
    final String errorMessage = info != null ? info.getParentInferenceErrorMessage(list) : null;
    if (errorMessage != null) {
      s+= "reason: "; 
      s += XmlStringUtil.escapeString(errorMessage).replaceAll("\n", "<br/>");
    }
    s+= "</body></html>";
    return s;
  }

  @SuppressWarnings("StringContatenationInLoop")
  @Language("HTML")
  private static String createMismatchedArgsHtmlTooltipArgumentsRow(final PsiExpression[] expressions, final PsiParameter[] parameters,
                                                                      final PsiSubstitutor substitutor, final int cols) {
    @Language("HTML")

    @NonNls String ms = "";
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      PsiType type = expression.getType();

      boolean showShort = showShortType(i, parameters, expressions, substitutor);
      @NonNls String mismatchColor = showShort ? null : MISMATCH_COLOR;
      ms += "<td> " + "<b><nobr>" + (i == 0 ? "(" : "")
            + "<font " + (showShort ? "" : "color=" + mismatchColor) + ">" +
            XmlStringUtil.escapeString(showShort ? type.getPresentableText() : JavaHighlightUtil.formatType(type))
            + "</font>"
            + (i == expressions.length - 1 ? ")" : ",") + "</nobr></b></td>";
    }
    for (int i = expressions.length; i < cols + 1; i++) {
      ms += "<td>" + (i == 0 ? "<b>()</b>" : "") +
            "&nbsp;</td>";
    }
    return ms;
  }

  @SuppressWarnings("StringContatenationInLoop")
  @Language("HTML")
  private static String createMismatchedArgsHtmlTooltipParamsRow(final PsiParameter[] parameters,
                                                                 final PsiSubstitutor substitutor,
                                                                 final PsiExpression[] expressions) {
    @NonNls String ms = "";
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiType type = substitutor.substitute(parameter.getType());
      ms += "<td><b><nobr>" + (i == 0 ? "(" : "") +
            XmlStringUtil.escapeString(showShortType(i, parameters, expressions, substitutor)
                                 ? type.getPresentableText()
                                 : JavaHighlightUtil.formatType(type))
            + (i == parameters.length - 1 ? ")" : ",") + "</nobr></b></td>";
    }
    return ms;
  }

  private static boolean showShortType(int i,
                                       PsiParameter[] parameters,
                                       PsiExpression[] expressions,
                                       PsiSubstitutor substitutor) {
    PsiExpression expression = i < expressions.length ? expressions[i] : null;
    if (expression == null) return true;
    PsiType paramType = i < parameters.length && parameters[i] != null
                        ? substitutor.substitute(parameters[i].getType())
                        : null;
    PsiType expressionType = expression.getType();
    return paramType != null && expressionType != null && TypeConversionUtil.isAssignable(paramType, expressionType);
  }


  static HighlightInfo checkMethodMustHaveBody(PsiMethod method, PsiClass aClass) {
    HighlightInfo errorResult = null;
    if (method.getBody() == null
        && !method.hasModifierProperty(PsiModifier.ABSTRACT)
        && !method.hasModifierProperty(PsiModifier.NATIVE)
        && aClass != null
        && !aClass.isInterface()
        && !PsiUtilCore.hasErrorElementChild(method)) {
      int start = method.getModifierList().getTextRange().getStartOffset();
      int end = method.getTextRange().getEndOffset();

      String description = JavaErrorMessages.message("missing.method.body");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description).create();
      if (HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, method.getModifierList()) == null) {
        QuickFixAction.registerQuickFixAction(errorResult,
                                              QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.ABSTRACT, true, false));
      }
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createAddMethodBodyFix(method));
    }
    return errorResult;
  }


  static HighlightInfo checkAbstractMethodInConcreteClass(PsiMethod method, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    PsiClass aClass = method.getContainingClass();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)
        && aClass != null
        && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)
        && !aClass.isEnum()
        && !PsiUtilCore.hasErrorElementChild(method)) {
      String description = JavaErrorMessages.message("abstract.method.in.non.abstract.class");
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


  static HighlightInfo checkConstructorName(PsiMethod method) {
    String methodName = method.getName();
    PsiClass aClass = method.getContainingClass();
    HighlightInfo errorResult = null;

    if (aClass != null) {
      String className = aClass instanceof PsiAnonymousClass ? null : aClass.getName();
      if (className == null || !Comparing.strEqual(methodName, className)) {
        PsiElement element = method.getNameIdentifier();
        String description = JavaErrorMessages.message("missing.return.type");
        errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
        if (className != null) {
          QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createRenameElementFix(method, className));
        }
      }
    }
    return errorResult;
  }

  @Nullable
  static HighlightInfo checkDuplicateMethod(PsiClass aClass,
                                            @NotNull PsiMethod method,
                                            @NotNull MostlySingularMultiMap<MethodSignature, PsiMethod> duplicateMethods) {
    if (aClass == null || method instanceof ExternallyDefinedPsiElement) return null;
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
      String description = JavaErrorMessages.message("duplicate.method",
                                                 JavaHighlightUtil.formatMethod(method),
                                                 HighlightUtil.formatClass(aClass));
      TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).
        range(method, textRange.getStartOffset(), textRange.getEndOffset()).
        descriptionAndTooltip(description).create();
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkMethodCanHaveBody(@NotNull PsiMethod method, @NotNull LanguageLevel languageLevel) {
    PsiClass aClass = method.getContainingClass();
    boolean hasNoBody = method.getBody() == null;
    boolean isInterface = aClass != null && aClass.isInterface();
    boolean isExtension = method.hasModifierProperty(PsiModifier.DEFAULT);
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    boolean isPrivate = method.hasModifierProperty(PsiModifier.PRIVATE);

    final List<IntentionAction> additionalFixes = new ArrayList<>();
    String description = null;
    if (hasNoBody) {
      if (isExtension) {
        description = JavaErrorMessages.message("extension.method.should.have.a.body");
        additionalFixes.add(QUICK_FIX_FACTORY.createAddMethodBodyFix(method));
      }
      else if (isInterface && isStatic && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        description = "Static methods in interfaces should have a body";
      }
    }
    else if (isInterface) {
      if (!isExtension && !isStatic && !isPrivate) {
        description = JavaErrorMessages.message("interface.methods.cannot.have.body");
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
          additionalFixes.add(QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.DEFAULT, true, false));
          additionalFixes.add(QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, true, false));
        }
      }
    }
    else if (isExtension) {
      description = JavaErrorMessages.message("extension.method.in.class");
    }
    else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      description = JavaErrorMessages.message("abstract.methods.cannot.have.a.body");
    }
    else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      description = JavaErrorMessages.message("native.methods.cannot.have.a.body");
    }
    if (description == null) return null;

    TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(method);
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    if (!hasNoBody) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteMethodBodyFix(method));
    }
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) && !isInterface) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.ABSTRACT, false, false));
    }
    for (IntentionAction intentionAction : additionalFixes) {
      QuickFixAction.registerQuickFixAction(info, intentionAction);
    }
    return info;
  }

  @Nullable
  static HighlightInfo checkConstructorCallMustBeFirstStatement(@NotNull PsiMethodCallExpression methodCall) {
    if (!RefactoringChangeUtil.isSuperOrThisMethodCall(methodCall)) return null;
    PsiElement codeBlock = methodCall.getParent().getParent();
    if (codeBlock instanceof PsiCodeBlock
        && codeBlock.getParent() instanceof PsiMethod
        && ((PsiMethod)codeBlock.getParent()).isConstructor()) {
      PsiElement prevSibling = methodCall.getParent().getPrevSibling();
      while (true) {
        if (prevSibling == null) return null;
        if (prevSibling instanceof PsiStatement) break;
        prevSibling = prevSibling.getPrevSibling();
      }
    }
    PsiReferenceExpression expression = methodCall.getMethodExpression();
    String message = JavaErrorMessages.message("constructor.call.must.be.first.statement", expression.getText() + "()");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCall).descriptionAndTooltip(message).create();
  }


  static HighlightInfo checkSuperAbstractMethodDirectCall(@NotNull PsiMethodCallExpression methodCallExpression) {
    PsiReferenceExpression expression = methodCallExpression.getMethodExpression();
    if (!(expression.getQualifierExpression() instanceof PsiSuperExpression)) return null;
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method != null && method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String message = JavaErrorMessages.message("direct.abstract.method.access", JavaHighlightUtil.formatMethod(method));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(methodCallExpression).descriptionAndTooltip(message).create();
    }
    return null;
  }


  static HighlightInfo checkConstructorCallsBaseClassConstructor(PsiMethod constructor,
                                                                        RefCountHolder refCountHolder,
                                                                        PsiResolveHelper resolveHelper) {
    if (!constructor.isConstructor()) return null;
    PsiClass aClass = constructor.getContainingClass();
    if (aClass == null) return null;
    if (aClass.isEnum()) return null;
    PsiCodeBlock body = constructor.getBody();
    if (body == null) return null;

    // check whether constructor call super(...) or this(...)
    PsiElement element = new PsiMatcherImpl(body)
      .firstChild(PsiMatchers.hasClass(PsiExpressionStatement.class))
      .firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class))
      .firstChild(PsiMatchers.hasClass(PsiReferenceExpression.class))
      .firstChild(PsiMatchers.hasClass(PsiKeyword.class))
      .getElement();
    if (element != null) return null;
    TextRange textRange = HighlightNamesUtil.getMethodDeclarationTextRange(constructor);
    PsiClassType[] handledExceptions = constructor.getThrowsList().getReferencedTypes();
    HighlightInfo info = HighlightClassUtil.checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, handledExceptions);
    if (info != null) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createInsertSuperFix(constructor));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddDefaultConstructorFix(aClass.getSuperClass()));
    }
    return info;
  }


  /**
   * @return error if static method overrides instance method or
   *         instance method overrides static. see JLS 8.4.6.1, 8.4.6.2
   */
  static HighlightInfo checkStaticMethodOverride(@NotNull PsiMethod method,@NotNull PsiFile containingFile) {
    // constructors are not members and therefor don't override class methods
    if (method.isConstructor()) {
      return null;
    }

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    final HierarchicalMethodSignature methodSignature = PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method);
    final List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
    if (superSignatures.isEmpty()) {
      return null;
    }

    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    for (HierarchicalMethodSignature signature : superSignatures) {
      final PsiMethod superMethod = signature.getMethod();
      final PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      final HighlightInfo highlightInfo = checkStaticMethodOverride(aClass, method, isStatic, superClass, superMethod,containingFile);
      if (highlightInfo != null) {
        return highlightInfo;
      }
    }
    return null;
  }

  private static HighlightInfo checkStaticMethodOverride(PsiClass aClass, PsiMethod method, boolean isMethodStatic, PsiClass superClass, PsiMethod superMethod,@NotNull PsiFile containingFile) {
    if (superMethod == null) return null;
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
      @NonNls final String messageKey = isMethodStatic
                                ? "static.method.cannot.override.instance.method"
                                : "instance.method.cannot.override.static.method";

      String description = JavaErrorMessages.message(messageKey,
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
        QuickFixAction.registerQuickFixAction(info,
                                              QUICK_FIX_FACTORY.createModifierListFix(superMethod, PsiModifier.STATIC, isMethodStatic, true));
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
    MethodSignatureBackedByPsiMethod returnTypeSubstitutable = superMethodSignatures.get(0);
    for (int i = 1; i < superMethodSignatures.size(); i++) {
      PsiMethod currentMethod = returnTypeSubstitutable.getMethod();
      PsiType currentType = returnTypeSubstitutable.getSubstitutor().substitute(currentMethod.getReturnType());

      MethodSignatureBackedByPsiMethod otherSuperSignature = superMethodSignatures.get(i);
      PsiMethod otherSuperMethod = otherSuperSignature.getMethod();
      PsiType otherSuperReturnType = otherSuperSignature.getSubstitutor().substitute(otherSuperMethod.getReturnType());

      PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(returnTypeSubstitutable,
                                                                                                  otherSuperSignature);
      if (unifyingSubstitutor != null) {
        otherSuperReturnType = unifyingSubstitutor.substitute(otherSuperReturnType);
        currentType = unifyingSubstitutor.substitute(currentType);
      }

      if (otherSuperReturnType == null || currentType == null || otherSuperReturnType.equals(currentType)) continue;

      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
        //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.4.8 Example 8.1.5-3
        if (!(otherSuperReturnType instanceof PsiPrimitiveType || currentType instanceof PsiPrimitiveType)) {
          if (otherSuperReturnType.isAssignableFrom(currentType)) continue;
          if (currentType.isAssignableFrom(otherSuperReturnType)) {
            returnTypeSubstitutable = otherSuperSignature;
            continue;
          }
        }
        if (otherSuperMethod.getTypeParameters().length > 0 && JavaGenericsUtil.isRawToGeneric(currentType, otherSuperReturnType)) continue;
      }
      return createIncompatibleReturnTypeMessage(otherSuperMethod, currentMethod, currentType, otherSuperReturnType,  
                                                 JavaErrorMessages.message("unrelated.overriding.methods.return.types"), TextRange.EMPTY_RANGE);
    }
    return null;
  }

  static HighlightInfo checkOverrideEquivalentInheritedMethods(PsiClass aClass, PsiFile containingFile, @NotNull LanguageLevel languageLevel) {
    String description = null;
    boolean appendImplementMethodFix = true;
    final Collection<HierarchicalMethodSignature> visibleSignatures = aClass.getVisibleSignatures();
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    Ultimate:
    for (HierarchicalMethodSignature signature : visibleSignatures) {
      PsiMethod method = signature.getMethod();
      if (!resolveHelper.isAccessible(method, aClass, null)) continue;
      List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

      boolean allAbstracts = method.hasModifierProperty(PsiModifier.ABSTRACT);
      final PsiClass containingClass = method.getContainingClass();
      if (aClass.equals(containingClass)) continue; //to be checked at method level

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
            description = JavaErrorMessages.message("static.method.cannot.override.instance.method",
                                                         JavaHighlightUtil.formatMethod(method),
                                                         HighlightUtil.formatClass(containingClass),
                                                         JavaHighlightUtil.formatMethod(superMethod),
                                                         HighlightUtil.formatClass(superMethod.getContainingClass()));
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
      final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      if (appendImplementMethodFix) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createImplementMethodsFix(aClass));
      }
      return highlightInfo;
    }
    return null;
  }


  static HighlightInfo checkConstructorHandleSuperClassExceptions(PsiMethod method) {
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
      String description = JavaErrorMessages.message("recursive.constructor.invocation");
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
    if (nextSibling instanceof PsiJavaToken && ((PsiJavaToken)nextSibling).getTokenType() == JavaTokenType.SEMICOLON) {
      return new TextRange(start, end + 1);
    }
    return range;
  }


  static void checkNewExpression(@NotNull PsiNewExpression expression,
                                 PsiType type,
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
    checkConstructorCall(typeResult, expression, type, classReference, holder, javaSdkVersion);
  }


  static void checkConstructorCall(@NotNull PsiClassType.ClassResolveResult typeResolveResult,
                                   @NotNull PsiConstructorCall constructorCall,
                                   @NotNull PsiType type,
                                   PsiJavaCodeReferenceElement classReference,
                                   @NotNull HighlightInfoHolder holder,
                                   @NotNull JavaSdkVersion javaSdkVersion) {
    PsiExpressionList list = constructorCall.getArgumentList();
    if (list == null) return;
    PsiClass aClass = typeResolveResult.getElement();
    if (aClass == null) return;
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(holder.getProject()).getResolveHelper();
    PsiClass accessObjectClass = null;
    if (constructorCall instanceof PsiNewExpression) {
      PsiExpression qualifier = ((PsiNewExpression)constructorCall).getQualifier();
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }
    }
    if (classReference != null && !resolveHelper.isAccessible(aClass, constructorCall, accessObjectClass)) {
      String description = HighlightUtil.buildProblemWithAccessDescription(classReference, typeResolveResult);
      PsiElement element = classReference.getReferenceNameElement();
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      HighlightUtil.registerAccessQuickFixAction(aClass, classReference, info, null);
      holder.add(info);
      return;
    }
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) {
      if (list.getExpressions().length != 0) {
        String constructorName = aClass.getName();
        String argTypes = buildArgTypesList(list);
        String description = JavaErrorMessages.message("wrong.constructor.arguments", constructorName+"()", argTypes);
        String tooltip = createMismatchedArgumentsHtmlTooltip(list, null, PsiParameter.EMPTY_ARRAY, constructorName, PsiSubstitutor.EMPTY, aClass);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).description(description).escapedToolTip(tooltip).navigationShift(+1).create();
        QuickFixAction.registerQuickFixAction(info, constructorCall.getTextRange(), QUICK_FIX_FACTORY.createCreateConstructorFromCallFix(constructorCall));
        if (classReference != null) {
          ConstructorParametersFixer.registerFixActions(classReference, constructorCall, info,getFixRange(list));
        }
        holder.add(info);
        return;
      }
      if (classReference != null && aClass.hasModifierProperty(PsiModifier.PROTECTED) && callingProtectedConstructorFromDerivedClass(constructorCall, aClass)) {
        holder.add(buildAccessProblem(classReference, typeResolveResult, aClass));
      } else if (aClass.isInterface() && constructorCall instanceof PsiNewExpression) {
        final PsiReferenceParameterList typeArgumentList = ((PsiNewExpression)constructorCall).getTypeArgumentList();
        if (typeArgumentList.getTypeArguments().length > 0) {
          holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeArgumentList)
            .descriptionAndTooltip("Anonymous class implements interface; cannot have type arguments").create());
        }
      }
    }
    else {
      PsiElement place = list;
      if (constructorCall instanceof PsiNewExpression) {
        final PsiAnonymousClass anonymousClass = ((PsiNewExpression)constructorCall).getAnonymousClass();
        if (anonymousClass != null) place = anonymousClass;
      }

      JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, place);
      MethodCandidateInfo result = null;
      if (results.length == 1) result = (MethodCandidateInfo)results[0];

      PsiMethod constructor = result == null ? null : result.getElement();

      boolean applicable = true;
      try {
        final PsiDiamondType diamondType = constructorCall instanceof PsiNewExpression ? PsiDiamondType.getDiamondType((PsiNewExpression)constructorCall) : null;
        final JavaResolveResult staticFactory = diamondType != null ? diamondType.getStaticFactory() : null;
        applicable = staticFactory instanceof MethodCandidateInfo ? ((MethodCandidateInfo)staticFactory).isApplicable()
                                                                  : result != null && result.isApplicable();
      }
      catch (IndexNotReadyException e) {
        // ignore
      }

      PsiElement infoElement = list.getTextLength() > 0 ? list : constructorCall;
      if (constructor == null) {
        String name = aClass.getName();
        name += buildArgTypesList(list);
        String description = JavaErrorMessages.message("cannot.resolve.constructor", name);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).navigationShift(+1).create();
        WrapExpressionFix.registerWrapAction(results, list.getExpressions(), info);
        registerFixesOnInvalidConstructorCall(constructorCall, classReference, list, aClass, constructors, results, infoElement, info);
        holder.add(info);
      }
      else {
        if (classReference != null && (!result.isAccessible() ||
                                       constructor.hasModifierProperty(PsiModifier.PROTECTED) && callingProtectedConstructorFromDerivedClass(constructorCall, aClass))) {
          holder.add(buildAccessProblem(classReference, result, constructor));
        }
        else if (!applicable) {
          String constructorName = HighlightMessageUtil.getSymbolName(constructor, result.getSubstitutor());
          String containerName = HighlightMessageUtil.getSymbolName(constructor.getContainingClass(), result.getSubstitutor());
          String argTypes = buildArgTypesList(list);
          String description = JavaErrorMessages.message("wrong.method.arguments", constructorName, containerName, argTypes);
          String toolTip = createMismatchedArgumentsHtmlTooltip(result, list);
          
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(infoElement).description(description).escapedToolTip(toolTip).navigationShift(+1).create();
          if (info != null) {
            JavaResolveResult[] methodCandidates = results;
            if (constructorCall instanceof PsiNewExpression) {
              methodCandidates = resolveHelper.getReferencedMethodCandidates((PsiCallExpression)constructorCall, true);
            }
            registerFixesOnInvalidConstructorCall(constructorCall, classReference, list, aClass, constructors, methodCandidates, infoElement, info);
            registerMethodReturnFixAction(info, result, constructorCall);
            holder.add(info);
          }
        }
        else {
          if (constructorCall instanceof PsiNewExpression) {
            PsiReferenceParameterList typeArgumentList = ((PsiNewExpression)constructorCall).getTypeArgumentList();
            HighlightInfo info = GenericsHighlightUtil.checkReferenceTypeArgumentList(constructor, typeArgumentList, result.getSubstitutor(), false, javaSdkVersion);
            if (info != null) {
              holder.add(info);
            }
          }
        }
      }
      
      if (result != null && !holder.hasErrorResults()) {
        holder.add(checkVarargParameterErasureToBeAccessible(result, constructorCall));
      }
    }
  }

  /**
   * If the compile-time declaration is applicable by variable arity invocation,
   * then where the last formal parameter type of the invocation type of the method is Fn[], 
   * it is a compile-time error if the type which is the erasure of Fn is not accessible at the point of invocation.
   */
  private static HighlightInfo checkVarargParameterErasureToBeAccessible(MethodCandidateInfo info, PsiCall place) {
    final PsiMethod method = info.getElement();
    if (info.isVarargs() || method.isVarArgs() && !PsiUtil.isLanguageLevel8OrHigher(place)) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiType componentType = ((PsiEllipsisType)parameters[parameters.length - 1].getType()).getComponentType();
      final PsiType substitutedTypeErasure = TypeConversionUtil.erasure(info.getSubstitutor().substitute(componentType));
      final PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(substitutedTypeErasure);
      if (targetClass != null && !PsiUtil.isAccessible(targetClass, place, null)) {
        final PsiExpressionList argumentList = place.getArgumentList();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .descriptionAndTooltip("Formal varargs element type " +
                                 PsiFormatUtil.formatClass(targetClass, PsiFormatUtilBase.SHOW_FQ_NAME) +
                                 " is inaccessible here")
          .range(argumentList != null ? argumentList : place)
          .create();
      }
    }
    return null;
  }

  private static void registerFixesOnInvalidConstructorCall(PsiConstructorCall constructorCall,
                                                            PsiJavaCodeReferenceElement classReference,
                                                            PsiExpressionList list,
                                                            PsiClass aClass,
                                                            PsiMethod[] constructors,
                                                            JavaResolveResult[] results, PsiElement infoElement, HighlightInfo info) {
    QuickFixAction
      .registerQuickFixAction(info, constructorCall.getTextRange(), QUICK_FIX_FACTORY.createCreateConstructorFromCallFix(constructorCall));
    if (classReference != null) {
      ConstructorParametersFixer.registerFixActions(classReference, constructorCall, info, getFixRange(infoElement));
      ChangeTypeArgumentsFix.registerIntentions(results, list, info, aClass);
      ConvertDoubleToFloatFix.registerIntentions(results, list, info, null);
    }
    registerChangeMethodSignatureFromUsageIntentions(results, list, info, null);
    PermuteArgumentsFix.registerFix(info, constructorCall, toMethodCandidates(results), getFixRange(list));
    registerChangeParameterClassFix(constructorCall, list, info);
    QuickFixAction.registerQuickFixAction(info, getFixRange(list), QUICK_FIX_FACTORY.createSurroundWithArrayFix(constructorCall,null));
    ChangeStringLiteralToCharInMethodCallFix.registerFixes(constructors, constructorCall, info);
  }

  private static HighlightInfo buildAccessProblem(@NotNull PsiJavaCodeReferenceElement classReference, JavaResolveResult result, PsiMember elementToFix) {
    String description = HighlightUtil.buildProblemWithAccessDescription(classReference, result);
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(classReference).descriptionAndTooltip(
      description).navigationShift(+1).create();
    if (result.isStaticsScopeCorrect()) {
      HighlightUtil.registerAccessQuickFixAction(elementToFix, classReference, info, result.getCurrentFileResolveScope());
    }
    return info;
  }

  private static boolean callingProtectedConstructorFromDerivedClass(PsiConstructorCall place, PsiClass constructorClass) {
    if (constructorClass == null) return false;
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

  private static String buildArgTypesList(PsiExpressionList list) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    PsiExpression[] args = list.getExpressions();
    for (int i = 0; i < args.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      PsiType argType = args[i].getType();
      builder.append(argType != null ? JavaHighlightUtil.formatType(argType) : "?");
    }
    builder.append(")");
    return builder.toString();
  }

  private static void registerChangeParameterClassFix(@NotNull PsiCall methodCall,
                                                      @NotNull PsiExpressionList list,
                                                      HighlightInfo highlightInfo) {
    final JavaResolveResult result = methodCall.resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    final PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    if (method == null) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != expressions.length) return;
    for (int i = 0; i < expressions.length; i++) {
      final PsiExpression expression = expressions[i];
      final PsiParameter parameter = parameters[i];
      final PsiType expressionType = expression.getType();
      final PsiType parameterType = substitutor.substitute(parameter.getType());
      if (expressionType == null || expressionType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(expressionType) || expressionType instanceof PsiArrayType) continue;
      if (parameterType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(parameterType) || parameterType instanceof PsiArrayType) continue;
      if (parameterType.isAssignableFrom(expressionType)) continue;
      PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
      PsiClass expressionClass = PsiUtil.resolveClassInType(expressionType);
      if (parameterClass == null || expressionClass == null) continue;
      if (expressionClass instanceof PsiAnonymousClass) continue;
      if (parameterClass.isInheritor(expressionClass, true)) continue;
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createChangeParameterClassFix(expressionClass, (PsiClassType)parameterType));
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntentions(@NotNull JavaResolveResult[] candidates,
                                                                       @NotNull PsiExpressionList list,
                                                                       @Nullable HighlightInfo highlightInfo,
                                                                       TextRange fixRange) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerChangeMethodSignatureFromUsageIntention(expressions, highlightInfo, fixRange, candidate, list);
    }
  }

  private static void registerChangeMethodSignatureFromUsageIntention(@NotNull PsiExpression[] expressions,
                                                                      @Nullable HighlightInfo highlightInfo,
                                                                      TextRange fixRange,
                                                                      @NotNull JavaResolveResult candidate,
                                                                      @NotNull PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method != null && context.getManager().isInProject(method)) {
      IntentionAction fix = QUICK_FIX_FACTORY.createChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context, false, 2);
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, fix);
      IntentionAction f2 = QUICK_FIX_FACTORY.createChangeMethodSignatureFromUsageReverseOrderFix(method, expressions, substitutor, context,
                                                                                                 false, 2);
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, f2);
    }
  }
}

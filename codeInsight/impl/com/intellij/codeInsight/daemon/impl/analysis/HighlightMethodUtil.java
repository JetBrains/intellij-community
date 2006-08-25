/*
 * Highlight method problems
 * User: cdr
 * Date: Aug 14, 2002
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HighlightMethodUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  public static String createClashMethodMessage(PsiMethod method1, PsiMethod method2, boolean showContainingClasses) {
    @NonNls String pattern = showContainingClasses ? "clash.methods.message.show.classes" : "clash.methods.message";

    return JavaErrorMessages.message(pattern,
                                     HighlightUtil.formatMethod(method1),
                                     HighlightUtil.formatMethod(method2),
                                     HighlightUtil.formatClass(method1.getContainingClass()),
                                     HighlightUtil.formatClass(method2.getContainingClass()));
  }

  //@top
  public static HighlightInfo checkMethodWeakerPrivileges(MethodSignatureBackedByPsiMethod methodSignature,
                                                          List<? extends MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                          boolean includeRealPositionInfo) {
    PsiMethod method = methodSignature.getMethod();
    PsiModifierList modifierList = method.getModifierList();
    if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) return null;
    int accessLevel = PsiUtil.getAccessLevel(modifierList);
    String accessModifier = PsiUtil.getAccessModifier(accessLevel);
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      int superAccessLevel = PsiUtil.getAccessLevel(superMethod.getModifierList());
      if (accessLevel < superAccessLevel) {
        String message = MessageFormat.format(JavaErrorMessages.message("weaker.privileges"),
                                              createClashMethodMessage(method, superMethod, true),
                                              accessModifier,
                                              PsiUtil.getAccessModifier(superAccessLevel));

        TextRange textRange;
        if (includeRealPositionInfo) {
          if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
            textRange = method.getNameIdentifier().getTextRange();
          }
          else {
            PsiElement keyword = PsiUtil.findModifierInList(modifierList, accessModifier);
            textRange = keyword.getTextRange();
          }
        }
        else {
          textRange = new TextRange(0, 0);
        }

        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
        IntentionAction fix =
          QUICK_FIX_FACTORY.createModifierListFix(method.getModifierList(), PsiUtil.getAccessModifier(superAccessLevel), true, false);
        QuickFixAction.registerQuickFixAction(highlightInfo, fix);
        return highlightInfo;
      }
    }
    return null;
  }

  //@top
  public static HighlightInfo checkMethodIncompatibleReturnType(MethodSignatureBackedByPsiMethod methodSignature,
                                                                List<? extends MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                                boolean includeRealPositionInfo) {
    PsiMethod method = methodSignature.getMethod();
    PsiType returnType = methodSignature.getSubstitutor().substitute(method.getReturnType());
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      if (!MethodSignatureUtil.isSubsignature(superMethodSignature, methodSignature)) continue;
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiType superReturnType = superMethodSignature.getSubstitutor().substitute(superMethod.getReturnType());
      if (returnType == null || superReturnType == null || method == superMethod) continue;
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      // EJB override rules are tricky, they are checked elsewhere in EJB SelectInEditorManager
      if (!Comparing.strEqual(method.getName(), superMethod.getName())
          || method.getParameterList().getParameters().length != superMethod.getParameterList().getParameters().length) {
        continue;
      }

      HighlightInfo highlightInfo = checkSuperMethodSignature(superMethod, superMethodSignature, superReturnType, method, methodSignature,
                                                              returnType, includeRealPositionInfo, JavaErrorMessages.message("incompatible.return.type"), method);
      if (highlightInfo != null) return highlightInfo;
    }

    return null;
  }

  private static HighlightInfo checkSuperMethodSignature(PsiMethod superMethod,
                                                         MethodSignatureBackedByPsiMethod superMethodSignature,
                                                         PsiType superReturnType,
                                                         PsiMethod method,
                                                         MethodSignatureBackedByPsiMethod methodSignature,
                                                         PsiType returnType,
                                                         boolean includeRealPositionInfo,
                                                         String detailMessage,
                                                         PsiMethod methodToHighlight) {
    if (superReturnType == null) return null;
    PsiType substitutedSuperReturnType;
    if (!superMethodSignature.isRaw()) {
      PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature,
                                                                                                  superMethodSignature);
      substitutedSuperReturnType = unifyingSubstitutor == null
                                   ? superReturnType
                                   : unifyingSubstitutor.substitute(superMethodSignature.getSubstitutor().substitute(superReturnType));
    }
    else {
      substitutedSuperReturnType = TypeConversionUtil.erasure(superReturnType);
    }

    if (returnType.equals(substitutedSuperReturnType)) return null;
    if (returnType.getDeepComponentType() instanceof PsiClassType &&
        substitutedSuperReturnType.getDeepComponentType() instanceof PsiClassType) {
      if (returnType.equals(TypeConversionUtil.erasure(superReturnType))) return null;
      if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(method)) <= 0 &&
          TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType)) {
        return null;
      }
    }

    return createIncompatibleReturnTypeMessage(methodToHighlight, method, superMethod, includeRealPositionInfo,
                                               substitutedSuperReturnType, returnType, detailMessage);
  }

  private static HighlightInfo createIncompatibleReturnTypeMessage(PsiMethod methodToHighlight,
                                                                   PsiMethod method,
                                                                   PsiMethod superMethod,
                                                                   boolean includeRealPositionInfo,
                                                                   PsiType substitutedSuperReturnType,
                                                                   PsiType returnType,
                                                                   String detailMessage) {
    String message = MessageFormat.format("{0}; {1}", createClashMethodMessage(method, superMethod, true), detailMessage);
    TextRange textRange = includeRealPositionInfo ? methodToHighlight.getReturnTypeElement().getTextRange() : new TextRange(0, 0);
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    IntentionAction fix = QUICK_FIX_FACTORY.createMethodReturnFix(method, substitutedSuperReturnType, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix);
    QuickFixAction.registerQuickFixAction(errorResult, new SuperMethodReturnFix(superMethod, returnType));

    return errorResult;
  }

  //@top
  static HighlightInfo checkMethodOverridesFinal(MethodSignatureBackedByPsiMethod methodSignature,
                                                 List<MethodSignatureBackedByPsiMethod> superMethodSignatures) {
    PsiMethod method = methodSignature.getMethod();
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      // strange things happen when super method is from Object and method from interface
      if (superMethod != null
          && superMethod.hasModifierProperty(PsiModifier.FINAL)) {
        String message = JavaErrorMessages.message("final.method.override",
                                                   HighlightUtil.formatMethod(method),
                                                   HighlightUtil.formatMethod(superMethod),
                                                   HighlightUtil.formatClass(superMethod.getContainingClass()));
        TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
        HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      textRange,
                                                                      message);
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(superMethod.getModifierList(), PsiModifier.FINAL, false, true);
        QuickFixAction.registerQuickFixAction(errorResult, fix);
        return errorResult;
      }
    }
    return null;
  }

  //@top
  public static HighlightInfo checkMethodIncompatibleThrows(MethodSignatureBackedByPsiMethod methodSignature,
                                                            List<? extends MethodSignatureBackedByPsiMethod> superMethodSignatures,
                                                            boolean includeRealPositionInfo, PsiClass analyzedClass) {
    PsiMethod method = methodSignature.getMethod();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, analyzedClass, PsiSubstitutor.EMPTY);
    PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
    PsiJavaCodeReferenceElement[] referenceElements;
    List<PsiElement> exceptionContexts;
    if (includeRealPositionInfo) {
      exceptionContexts = new ArrayList<PsiElement>();
      referenceElements = method.getThrowsList().getReferenceElements();
    }
    else {
      exceptionContexts = null;
      referenceElements = null;
    }
    List<PsiClassType> checkedExceptions = new ArrayList<PsiClassType>();
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
        PsiClassType exception = checkedExceptions.get(index);
        String message = JavaErrorMessages.message("overridden.method.does.not.throw",
                                                   createClashMethodMessage(method, superMethod, true),
                                                   HighlightUtil.formatType(exception));
        TextRange textRange;
        if (includeRealPositionInfo) {
          PsiElement exceptionContext = exceptionContexts.get(index);
          textRange = exceptionContext.getTextRange();
        }
        else {
          textRange = new TextRange(0, 0);
        }
        HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMethodThrowsFix(method, exception, false, false));
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMethodThrowsFix(superMethod, exception, true, true));
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
    if (substitutorForMethod == null) return -1;
    for (int i = 0; i < checkedExceptions.size(); i++) {
      PsiType exception = substitutorForDerivedClass.substitute(substitutorForMethod.substitute(checkedExceptions.get(i)));
      if (!isMethodThrows(superMethod, substitutorForMethod, exception, substitutorForDerivedClass)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isMethodThrows(PsiMethod method, PsiSubstitutor substitutorForMethod, PsiType exception, PsiSubstitutor substitutorForDerivedClass) {
    PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
    for (PsiClassType thrownException1 : thrownExceptions) {
      PsiType thrownException = substitutorForMethod.substitute(thrownException1);
      thrownException = substitutorForDerivedClass.substitute(thrownException);
      if (TypeConversionUtil.isAssignable(thrownException, exception)) return true;
    }
    return false;
  }

  //@top
  public static HighlightInfo checkMethodCall(PsiMethodCallExpression methodCall,
                                              PsiResolveHelper resolveHelper) {
    PsiExpressionList list = methodCall.getArgumentList();
    PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
    JavaResolveResult resolveResult = referenceToMethod.advancedResolve(true);
    PsiElement element = resolveResult.getElement();

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
    if (isDummy) return null;
    HighlightInfo highlightInfo;

    if (element instanceof PsiMethod && resolveResult.isValidResult()) {
      TextRange fixRange = getFixRange(methodCall);
      highlightInfo = HighlightUtil.checkUnhandledExceptions(methodCall, fixRange);

      if (highlightInfo == null) {
        highlightInfo = GenericsHighlightUtil.checkUncheckedCall(resolveResult, methodCall);
      }
      if (highlightInfo == null) {
        highlightInfo = GenericsHighlightUtil.checkGenericCallWithRawArguments(resolveResult, methodCall);
      }
    }
    else {
      PsiMethod resolvedMethod = null;
      MethodCandidateInfo info = null;
      if (resolveResult instanceof MethodCandidateInfo) {
        info = (MethodCandidateInfo)resolveResult;
        resolvedMethod = info.getElement();
      }

      if (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect()) {
        highlightInfo = checkAmbiguousMethodCall(referenceToMethod, list, element, resolveResult, methodCall, resolveHelper);
      }
      else if (info != null && !info.isApplicable()) {
        if (info.isTypeArgumentsApplicable()) {
          String methodName = HighlightMessageUtil.getSymbolName(element, resolveResult.getSubstitutor());
          PsiElement parent = element.getParent();
          String containerName = parent == null ? "" : HighlightMessageUtil.getSymbolName(parent, resolveResult.getSubstitutor());
          String argTypes = HighlightUtil.buildArgTypesList(list);
          String description = JavaErrorMessages.message("wrong.method.arguments", methodName, containerName, argTypes);
          String toolTip = parent instanceof PsiClass ?
                           createMismatchedArgumentsHtmlTooltip(info, list) : description;
          highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description, toolTip);
          registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
          highlightInfo.navigationShift = +1;
        }
        else {
          PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
          PsiReferenceParameterList typeArgumentList = methodCall.getTypeArgumentList();
          if (typeArgumentList.getTypeArguments().length == 0 && resolvedMethod.getTypeParameters().length > 0) {
            highlightInfo = GenericsHighlightUtil.checkInferredTypeArguments(resolvedMethod, methodCall, resolveResult.getSubstitutor());
          }
          else {
            highlightInfo = GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(element, methodExpression,
                                                                                           resolveResult.getSubstitutor());
          }
        }
      }
      else {
        highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, methodCall, JavaErrorMessages.message("method.call.expected"));
        if (element instanceof PsiClass) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new InsertNewFix(methodCall, (PsiClass)element));
        }
        else {
          TextRange range = getFixRange(methodCall);
          QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreateMethodFromUsageAction(methodCall), null, null);
          QuickFixAction.registerQuickFixAction(highlightInfo, range, new CreatePropertyFromUsageAction(methodCall), null, null);
        }
      }
    }
   /* if (highlightInfo == null) {
      highlightInfo =
      DeprecationInspection.checkDeprecated(element, referenceToMethod.getReferenceNameElement());
    }*/
    if (highlightInfo == null) {
      highlightInfo =
      GenericsHighlightUtil.checkParameterizedReferenceTypeArguments(element, referenceToMethod, resolveResult.getSubstitutor());
    }
    return highlightInfo;
  }

  private static HighlightInfo checkAmbiguousMethodCall(final PsiReferenceExpression referenceToMethod,
                                                        final PsiExpressionList list,
                                                        final PsiElement element,
                                                        final JavaResolveResult resolveResult,
                                                        final PsiMethodCallExpression methodCall, final PsiResolveHelper resolveHelper) {
    JavaResolveResult[] resolveResults = referenceToMethod.multiResolve(true);
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
    List<MethodCandidateInfo> candidateList = new ArrayList<MethodCandidateInfo>();

    for (JavaResolveResult result : resolveResults) {
      if (!(result instanceof MethodCandidateInfo)) continue;
      MethodCandidateInfo candidate = (MethodCandidateInfo)result;
      if (candidate.isAccessible()) candidateList.add(candidate);
    }

    String description;
    String toolTip;
    PsiElement elementToHighlight;
    HighlightInfoType highlightInfoType = HighlightInfoType.ERROR;
    if (methodCandidate2 != null) {
      String m1 = PsiFormatUtil.formatMethod(methodCandidate1.getElement(),
                                             methodCandidate1.getSubstitutor(),
                                             PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME |
                                             PsiFormatUtil.SHOW_PARAMETERS,
                                             PsiFormatUtil.SHOW_TYPE);
      String m2 = PsiFormatUtil.formatMethod(methodCandidate2.getElement(),
                                             methodCandidate2.getSubstitutor(),
                                             PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME |
                                             PsiFormatUtil.SHOW_PARAMETERS,
                                             PsiFormatUtil.SHOW_TYPE);
      description = JavaErrorMessages.message("ambiguous.method.call", m1, m2);
      toolTip = createAmbiguousMethodHtmlTooltip(new MethodCandidateInfo[]{methodCandidate1, methodCandidate2});
      elementToHighlight = list;
    }
    else {
      if (element != null && !resolveResult.isAccessible()) {
        description = HighlightUtil.buildProblemWithAccessDescription(referenceToMethod, resolveResult);
        elementToHighlight = referenceToMethod.getReferenceNameElement();
      }
      else if (element != null && !resolveResult.isStaticsScopeCorrect()) {
        description = HighlightUtil.buildProblemWithStaticDescription(element);
        elementToHighlight = referenceToMethod.getReferenceNameElement();
      }
      else {
        String methodName = referenceToMethod.getReferenceName() + HighlightUtil.buildArgTypesList(list);
        description = JavaErrorMessages.message("cannot.resolve.method", methodName);
        if (candidateList.isEmpty()) {
          elementToHighlight = referenceToMethod.getReferenceNameElement();
          highlightInfoType = HighlightInfoType.WRONG_REF;
        }
        else {
          elementToHighlight = list;
        }
      }
      toolTip = description;
    }
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(highlightInfoType, elementToHighlight, description, toolTip);
    if (methodCandidate2 == null) {
      registerMethodCallIntentions(highlightInfo, methodCall, list, resolveHelper);
    }
    if (!resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect() && methodCandidate2 != null) {
      HighlightUtil.registerAccessQuickFixAction((PsiMember)element, referenceToMethod, highlightInfo, resolveResult.getCurrentFileResolveScope());
    }
    if (!resolveResult.isStaticsScopeCorrect()) {
      HighlightUtil.registerStaticProblemQuickFixAction(element, highlightInfo, referenceToMethod);
    }

    MethodCandidateInfo[] candidates = candidateList.toArray(new MethodCandidateInfo[candidateList.size()]);
    TextRange fixRange = getFixRange(elementToHighlight);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, methodCall, highlightInfo, fixRange);
    PermuteArgumentsFix.registerFix(highlightInfo, methodCall, candidates, fixRange);
    WrapExpressionFix.registerWrapAction(candidates, list.getExpressions(), highlightInfo);
    ChangeParameterClassFix.registerQuickFixActions(methodCall, list, highlightInfo);
    return highlightInfo;
  }

  private static void registerMethodCallIntentions(HighlightInfo highlightInfo,
                                                   PsiMethodCallExpression methodCall,
                                                   PsiExpressionList list, PsiResolveHelper resolveHelper) {
    TextRange fixRange = getFixRange(methodCall);
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, new CreateMethodFromUsageAction(methodCall), null, null);
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, new CreateConstructorFromSuperAction(methodCall), null, null);
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, new CreateConstructorFromThisAction(methodCall), null, null);
    QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, new CreatePropertyFromUsageAction(methodCall), null, null);
    CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates(methodCall, false);
    CastMethodArgumentFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    PermuteArgumentsFix.registerFix(highlightInfo, methodCall, methodCandidates, fixRange);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(methodCandidates, methodCall, highlightInfo, fixRange);
    registerMethodAccessLevelIntentions(methodCandidates, methodCall, list, highlightInfo);
    ChangeMethodSignatureFromUsageFix.registerIntentions(methodCandidates, list, highlightInfo, fixRange);
    WrapExpressionFix.registerWrapAction(methodCandidates, list.getExpressions(), highlightInfo);
    ChangeParameterClassFix.registerQuickFixActions(methodCall, list, highlightInfo);
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

  private static String createAmbiguousMethodHtmlTooltip(MethodCandidateInfo[] methodCandidates) {
    return JavaErrorMessages.message("ambiguous.method.html.tooltip",
                                     new Integer(methodCandidates[0].getElement().getParameterList().getParameters().length + 2),
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

  private static String createAmbiguousMethodHtmlTooltipMethodRow(final MethodCandidateInfo methodCandidate) {
    PsiMethod method = methodCandidate.getElement();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
    @NonNls String ms = "<td><b>" + method.getName() + "</b></td>";

    for (int j = 0; j < parameters.length; j++) {
      PsiParameter parameter = parameters[j];
      PsiType type = substitutor.substitute(parameter.getType());
      ms += "<td><b>" + (j == 0 ? "(" : "") +
            XmlUtil.escapeString(type.getPresentableText())
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
    return createMismatchedArgumentsHtmlTooltip(list, parameters, methodName, substitutor, aClass);
  }

  private static String createMismatchedArgumentsHtmlTooltip(PsiExpressionList list,
                                                             PsiParameter[] parameters,
                                                             String methodName,
                                                             PsiSubstitutor substitutor,
                                                             PsiClass aClass) {
    PsiExpression[] expressions = list.getExpressions();
    int cols = Math.max(parameters.length, expressions.length);

    @NonNls String parensizedName = methodName + (parameters.length == 0 ? "(&nbsp;)&nbsp;" : "");
    return JavaErrorMessages.message(
      "argument.mismatch.html.tooltip",
      new Integer(cols - parameters.length + 1), parensizedName,
      HighlightUtil.formatClass(aClass, false),
      createMismatchedArgsHtmlTooltipParamsRow(parameters, substitutor, expressions),
      createMismatchedArgsHtmlTooltipExpressionsRow(expressions, parameters, substitutor, cols)
    );
  }

  private static String createMismatchedArgsHtmlTooltipExpressionsRow(final PsiExpression[] expressions, final PsiParameter[] parameters,
                                                                      final PsiSubstitutor substitutor, final int cols) {
    @NonNls String ms = "";
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      PsiType type = expression.getType();

      @NonNls String mismatchColor = showShortType(i, parameters, expressions, substitutor) ? null : "red";
      ms += "<td> " + "<b><nobr>" + (i == 0 ? "(" : "")
            + "<font " + (mismatchColor == null ? "" : "color=" + mismatchColor) + ">" +
            XmlUtil.escapeString(showShortType(i, parameters, expressions, substitutor)
                                 ? type.getPresentableText()
                                 : HighlightUtil.formatType(type))
            + "</font>"
            + (i == expressions.length - 1 ? ")" : ",") + "</nobr></b></td>";
    }
    for (int i = expressions.length; i < cols + 1; i++) {
      ms += "<td>" + (i == 0 ? "<b>()</b>" : "") +
            "&nbsp;</td>";
    }
    return ms;
  }

  private static String createMismatchedArgsHtmlTooltipParamsRow(final PsiParameter[] parameters,
                                                                 final PsiSubstitutor substitutor,
                                                                 final PsiExpression[] expressions) {
    @NonNls String ms = "";
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiType type = substitutor.substituteAndCapture(parameter.getType());
      ms += "<td><b><nobr>" + (i == 0 ? "(" : "") +
            XmlUtil.escapeString(showShortType(i, parameters, expressions, substitutor)
                                 ? type.getPresentableText()
                                 : HighlightUtil.formatType(type))
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
                        ? substitutor.substituteAndCapture(parameters[i].getType())
                        : null;
    return paramType != null && TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression);
  }

  //@top
  static HighlightInfo checkMethodMustHaveBody(PsiMethod method, PsiClass aClass) {
    HighlightInfo errorResult = null;
    if (method.getBody() == null
        && !method.hasModifierProperty(PsiModifier.ABSTRACT)
        && !method.hasModifierProperty(PsiModifier.NATIVE)
        && aClass != null
        && !aClass.isInterface()
        && !PsiUtil.hasErrorElementChild(method)) {
      int start = method.getModifierList().getTextRange().getStartOffset();
      int end = method.getTextRange().getEndOffset();

      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      start, end,
                                                      JavaErrorMessages.message("missing.method.body"));
      if (HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, method.getModifierList(),
                                                HighlightUtil.ourMethodIncompatibleModifiers) == null) {
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(method.getModifierList(), PsiModifier.ABSTRACT, true, false);
        QuickFixAction.registerQuickFixAction(errorResult, fix);
      }
      QuickFixAction.registerQuickFixAction(errorResult, new AddMethodBodyFix(method));
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkAbstractMethodInConcreteClass(PsiMethod method, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    PsiClass aClass = method.getContainingClass();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)
        && aClass != null
        && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)
        && !aClass.isEnum()
        && !PsiUtil.hasErrorElementChild(method)) {
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      elementToHighlight,
                                                      JavaErrorMessages.message("abstract.method.in.non.abstract.class"));
      if (method.getBody() != null) {
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(method.getModifierList(), PsiModifier.ABSTRACT, false, false);
        QuickFixAction.registerQuickFixAction(errorResult, fix);
      }
      QuickFixAction.registerQuickFixAction(errorResult, new AddMethodBodyFix(method));
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass.getModifierList(), PsiModifier.ABSTRACT, true, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkConstructorName(PsiMethod method) {
    String methodName = method.getName();
    PsiClass aClass = method.getContainingClass();
    HighlightInfo errorResult = null;

    if (aClass != null) {
      String className = aClass instanceof PsiAnonymousClass ? null : aClass.getName();
      if (!Comparing.strEqual(methodName, className)) {
        errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, method.getNameIdentifier(),
                                                        JavaErrorMessages.message("missing.return.type"));
      }
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkDuplicateMethod(PsiClass aClass, PsiMethod method) {
    if (aClass == null) return null;
    MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    int methodCount = 0;
    final PsiMethod[] methodsByName = aClass.findMethodsByName(method.getName(), false);
    for (PsiMethod other : methodsByName) {
      if (other.isConstructor() == method.isConstructor() &&
          other.getSignature(PsiSubstitutor.EMPTY).equals(methodSignature)) {
        methodCount++;
        if (methodCount > 1) break;
      }
    }

    if (methodCount == 1 && aClass.isEnum() &&
        GenericsHighlightUtil.isEnumSyntheticMethod(methodSignature, aClass.getProject())) {
      methodCount++;
    }
    if (methodCount > 1) {
      String message = JavaErrorMessages.message("duplicate.method",
                                                 HighlightUtil.formatMethod(method),
                                                 HighlightUtil.formatClass(aClass));
      TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    }
    return null;
  }

  //@top
  public static HighlightInfo checkMethodCanHaveBody(PsiMethod method) {
    if (method.getBody() == null) return null;
    PsiClass aClass = method.getContainingClass();

    String message = null;
    if (aClass != null && aClass.isInterface()) {
      message = JavaErrorMessages.message("interface.methods.cannot.have.body");
    }
    else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      message = JavaErrorMessages.message("abstract.methods.cannot.have.a.body");
    }
    else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      message = JavaErrorMessages.message("native.methods.cannot.have.a.body");
    }

    if (message != null) {
      TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
      QuickFixAction.registerQuickFixAction(info, new DeleteMethodBodyFix(method));
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && aClass != null && !aClass.isInterface()) {
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(method.getModifierList(), PsiModifier.ABSTRACT, false, false);
        QuickFixAction.registerQuickFixAction(info, fix);
      }
      return info;
    }
    return null;
  }

  //@top
  static HighlightInfo checkConstructorCallMustBeFirstStatement(PsiReferenceExpression expression) {
    String text = expression.getText();
    PsiElement methodCall = expression.getParent();
    if (!HighlightUtil.isSuperOrThisMethodCall(methodCall)) return null;
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
    String message = JavaErrorMessages.message("constructor.call.must.be.first.statement", text + "()");
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression.getParent(), message);
  }

  //@top
  public static HighlightInfo checkAbstractMethodDirectCall(PsiSuperExpression expr) {
    if (expr.getParent() instanceof PsiReferenceExpression
        && expr.getParent().getParent() instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expr.getParent().getParent();
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method != null && method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        String message = JavaErrorMessages.message("direct.abstract.method.access", HighlightUtil.formatMethod(method));
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, methodCallExpression, message);
      }
    }
    return null;
  }

  //@top
  public static HighlightInfo checkConstructorCallsBaseClassConstructor(PsiMethod constructor,
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
      .firstChild(PsiMatcherImpl.hasClass(PsiExpressionStatement.class))
      .firstChild(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
      .firstChild(PsiMatcherImpl.hasClass(PsiReferenceExpression.class))
      .firstChild(PsiMatcherImpl.hasClass(PsiKeyword.class))
      .getElement();
    if (element != null) return null;
    TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(constructor);
    PsiClassType[] handledExceptions = constructor.getThrowsList().getReferencedTypes();
    HighlightInfo info = HighlightClassUtil.checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, handledExceptions);
    if (info != null) {
      QuickFixAction.registerQuickFixAction(info, new InsertSuperFix(constructor));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddDefaultConstructorFix(aClass.getSuperClass()));
    }
    return info;
  }

  //@top
  /**
   * @return error if static method overrides instance method or
   *         instance method overrides static. see JLS 8.4.6.1, 8.4.6.2
   */
  public static HighlightInfo checkStaticMethodOverride(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiClass superClass = aClass.getSuperClass();
    PsiMethod superMethod = superClass == null
                            ? null
                            : MethodSignatureUtil.findMethodBySignature(superClass, method, true);

    HighlightInfo highlightInfo = checkStaticMethodOverride(aClass, method, superClass, superMethod, true);
    if (highlightInfo != null) return highlightInfo;
    PsiClass[] interfaces = aClass.getInterfaces();
    for (PsiClass aInterfaces : interfaces) {
      superClass = aInterfaces;
      superMethod = MethodSignatureUtil.findMethodBySignature(superClass, method, true);
      highlightInfo = checkStaticMethodOverride(aClass, method, superClass, superMethod, true);
      if (highlightInfo != null) return highlightInfo;
    }
    return highlightInfo;
  }

  //@top
  public static HighlightInfo checkStaticMethodOverride(PsiClass aClass,
                                                        PsiMethod method,
                                                        PsiClass superClass,
                                                        PsiMethod superMethod,
                                                        boolean includeRealPositionInfo) {
    if (superMethod == null) return null;
    PsiManager manager = superMethod.getManager();
    PsiModifierList superModifierList = superMethod.getModifierList();
    PsiModifierList modifierList = method.getModifierList();
    if (superModifierList.hasModifierProperty(PsiModifier.PRIVATE)) return null;
    if (superModifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
        && !manager.arePackagesTheSame(aClass, superClass)) {
      return null;
    }
    boolean isMethodStatic = modifierList.hasModifierProperty(PsiModifier.STATIC);
    boolean isSuperMethodStatic = superModifierList.hasModifierProperty(PsiModifier.STATIC);
    if (isMethodStatic != isSuperMethodStatic) {
      TextRange textRange = includeRealPositionInfo ? HighlightUtil.getMethodDeclarationTextRange(method) : new TextRange(0, 0);
      @NonNls final String messageKey = isMethodStatic
                                ? "static.method.cannot.override.instance.method"
                                : "instance.method.cannot.override.static.method";

      String message = JavaErrorMessages.message(messageKey,
                                                 HighlightUtil.formatMethod(method),
                                                 HighlightUtil.formatClass(aClass),
                                                 HighlightUtil.formatMethod(superMethod),
                                                 HighlightUtil.formatClass(superClass));

      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      textRange,
                                                                      message);
      if (!isSuperMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, modifierList) == null) {
        IntentionAction fix =
          QUICK_FIX_FACTORY.createModifierListFix(method.getModifierList(), PsiModifier.STATIC, isSuperMethodStatic, false);
        QuickFixAction.registerQuickFixAction(highlightInfo, fix);
      }
      if (manager.isInProject(superMethod) &&
          (!isMethodStatic || HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, superModifierList) == null)) {
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(superMethod.getModifierList(), PsiModifier.STATIC, isMethodStatic, true);
        QuickFixAction.registerQuickFixAction(highlightInfo, fix);
      }
      return highlightInfo;
    }

    return null;
  }

  private static HighlightInfo checkInterfaceInheritedMethodsReturnTypes(List<? extends MethodSignatureBackedByPsiMethod> superMethodSignatures) {
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

      if (otherSuperReturnType == null || currentType == null) continue;
      if (otherSuperReturnType.equals(currentType)) continue;

      if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(currentMethod)) <= 0) {
        if (otherSuperReturnType.isAssignableFrom(currentType)) continue;
        if (currentType.isAssignableFrom(otherSuperReturnType)) {
          returnTypeSubstitutable = otherSuperSignature;
          continue;
        }
      }
      return createIncompatibleReturnTypeMessage(currentMethod, currentMethod, otherSuperMethod, false, otherSuperReturnType,
                                                 currentType, JavaErrorMessages.message("unrelated.overriding.methods.return.types"));
    }
    return null;
  }

  public static HighlightInfo checkOverrideEquivalentInheritedMethods(PsiClass aClass) {
    String errorDescription = null;
    final Collection<HierarchicalMethodSignature> visibleSignatures = aClass.getVisibleSignatures();
    PsiResolveHelper resolveHelper = aClass.getManager().getResolveHelper();
    Ultimate:
    for (HierarchicalMethodSignature signature : visibleSignatures) {
      PsiMethod method = signature.getMethod();
      if (!resolveHelper.isAccessible(method, aClass, null)) continue;
      List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

      boolean allAbstracts = method.hasModifierProperty(PsiModifier.ABSTRACT);
      HighlightInfo highlightInfo = null;
      final PsiClass containingClass = method.getContainingClass();
      if (aClass.isInterface() && !containingClass.isInterface()) continue;
      if (!allAbstracts) {
        if (!aClass.equals(containingClass)) {
          highlightInfo = checkMethodIncompatibleReturnType(signature, superSignatures, false);
        }
      }
      else {
        if (!containingClass.equals(aClass)) {
          superSignatures = new ArrayList<HierarchicalMethodSignature>(superSignatures);
          superSignatures.add(signature);
        }
        highlightInfo = checkInterfaceInheritedMethodsReturnTypes(superSignatures);
      }
      if (highlightInfo != null) errorDescription = highlightInfo.description;

      if (aClass.equals(containingClass)) continue; //to be checked at method level

      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        for (HierarchicalMethodSignature superSignature : superSignatures) {
          PsiMethod superMethod = superSignature.getMethod();
          if (!superMethod.hasModifierProperty(PsiModifier.STATIC)) {
            errorDescription = JavaErrorMessages.message("static.method.cannot.override.instance.method",
                                                         HighlightUtil.formatMethod(method),
                                                         HighlightUtil.formatClass(containingClass),
                                                         HighlightUtil.formatMethod(superMethod),
                                                         HighlightUtil.formatClass(superMethod.getContainingClass()));
            break Ultimate;
          }
        }
        continue;
      }

      if (errorDescription == null) {
        highlightInfo = checkMethodIncompatibleThrows(signature, superSignatures, false, aClass);
        if (highlightInfo != null) errorDescription = highlightInfo.description;
      }

      if (errorDescription == null) {
        highlightInfo = checkMethodWeakerPrivileges(signature, superSignatures, false);
        if (highlightInfo != null) errorDescription = highlightInfo.description;
      }

      if (errorDescription != null) break;
    }


    if (errorDescription != null) {
      // show error info at the class level
      TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               textRange,
                                               errorDescription);
    }
    return null;
  }

  //@top
  public static HighlightInfo checkConstructorHandleSuperClassExceptions(PsiMethod method) {
    if (!method.isConstructor()) {
      return null;
    }
    PsiCodeBlock body = method.getBody();
    PsiStatement[] statements = body == null ? null : body.getStatements();
    if (statements == null) return null;

    // if we have unhandled exception inside method body, we could not have been called here,
    // so the only problem it can catch here is with super ctr only
    PsiClassType[] unhandled = ExceptionUtil.collectUnhandledExceptions(method, method.getContainingClass());
    if (unhandled == null || unhandled.length == 0) return null;
    String description = HighlightUtil.getUnhandledExceptionsDescriptor(unhandled);
    TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                    textRange,
                                                                    description);
    for (PsiClassType exception : unhandled) {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createMethodThrowsFix(method, exception, true, false));
    }
    return highlightInfo;
  }

  //@top
  public static HighlightInfo checkRecursiveConstructorInvocation(PsiMethod method) {
    if (HighlightControlFlowUtil.isRecursivelyCalledConstructor(method)) {
      TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, JavaErrorMessages.message("recursive.constructor.invocation"));
    }
    return null;
  }

  public static TextRange getFixRange(PsiElement element) {
    TextRange range = element.getTextRange();
    int start = range.getStartOffset();
    int end = range.getEndOffset();

    PsiElement nextSibling = element.getNextSibling();
    if (nextSibling instanceof PsiJavaToken && ((PsiJavaToken)nextSibling).getTokenType() == JavaTokenType.SEMICOLON) {
      return new TextRange(start, end + 1);
    }
    return range;
  }

  //@top
  static HighlightInfo checkNewExpression(PsiNewExpression expression) {
    PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) return null;
    PsiClassType.ClassResolveResult typeResult = ((PsiClassType)type).resolveGenerics();
    PsiClass aClass = typeResult.getElement();
    if (aClass == null) return null;
    if (aClass instanceof PsiAnonymousClass) {
      type = ((PsiAnonymousClass)aClass).getBaseClassType();
      typeResult = ((PsiClassType)type).resolveGenerics();
      aClass = typeResult.getElement();
      if (aClass == null) return null;
    }

    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    if (classReference == null) {
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) classReference = anonymousClass.getBaseClassReference();
    }
    return checkConstructorCall(typeResult, expression, type, classReference);
  }

  //@top
  public static HighlightInfo checkConstructorCall(PsiClassType.ClassResolveResult typeResolveResult,
                                                   PsiConstructorCall constructorCall,
                                                   PsiType type,
                                                   PsiJavaCodeReferenceElement classReference) {
    PsiExpressionList list = constructorCall.getArgumentList();
    if (list == null) return null;
    PsiClass aClass = typeResolveResult.getElement();
    if (aClass == null) return null;
    final PsiResolveHelper resolveHelper = constructorCall.getManager().getResolveHelper();
    PsiClass accessObjectClass = null;
    if (constructorCall instanceof PsiNewExpression) {
      PsiExpression qualifier = ((PsiNewExpression)constructorCall).getQualifier();
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }
    }
    if (classReference != null && !resolveHelper.isAccessible(aClass, constructorCall, accessObjectClass)) {
      String description = HighlightUtil.buildProblemWithAccessDescription(classReference, typeResolveResult);
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, classReference.getReferenceNameElement(), description);
      HighlightUtil.registerAccessQuickFixAction(aClass, classReference, info, null);
      return info;
    }
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) {
      if (list.getExpressions().length != 0) {
        String constructorName = aClass.getName();
        String argTypes = HighlightUtil.buildArgTypesList(list);
        String description = JavaErrorMessages.message("wrong.constructor.arguments", constructorName+"()", argTypes);
        String tooltip = createMismatchedArgumentsHtmlTooltip(list, PsiParameter.EMPTY_ARRAY, constructorName, PsiSubstitutor.EMPTY, aClass);
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description, tooltip);
        QuickFixAction.registerQuickFixAction(info, constructorCall.getTextRange(), new CreateConstructorFromCallAction(constructorCall), null, null);
        if (classReference != null) {
          CastConstructorParametersFix.registerCastActions(classReference, constructorCall, info,getFixRange(list));
        }
        info.navigationShift = +1;
        return info;
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
      if (constructor == null) {
        String name = aClass.getName();
        name += HighlightUtil.buildArgTypesList(list);
        String description = JavaErrorMessages.message("cannot.resolve.constructor", name);
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description);
        QuickFixAction.registerQuickFixAction(info, constructorCall.getTextRange(), new CreateConstructorFromCallAction(constructorCall), null, null);
        if (classReference != null) {
          CastConstructorParametersFix.registerCastActions(classReference, constructorCall, info,getFixRange(list));
        }
        WrapExpressionFix.registerWrapAction(results, list.getExpressions(), info);
        info.navigationShift = +1;
        return info;
      }
      else {
        if (!result.isAccessible() || callingProtectedConstructorFromDerivedClass(constructor, constructorCall)) {
          String description = HighlightUtil.buildProblemWithAccessDescription(classReference, result);
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, description);
          info.navigationShift = +1;
          if (classReference != null && result.isStaticsScopeCorrect()) {
            HighlightUtil.registerAccessQuickFixAction(constructor, classReference, info, result.getCurrentFileResolveScope());
          }
          return info;
        }
        else if (!result.isApplicable()) {
          String constructorName = HighlightMessageUtil.getSymbolName(constructor, result.getSubstitutor());
          String containerName = HighlightMessageUtil.getSymbolName(constructor.getParent(), result.getSubstitutor());
          String argTypes = HighlightUtil.buildArgTypesList(list);
          String description = JavaErrorMessages.message("wrong.method.arguments", constructorName, containerName, argTypes);
          String toolTip = createMismatchedArgumentsHtmlTooltip(result, list);
          PsiElement infoElement = list.getTextLength() > 0 ? list : constructorCall;
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, infoElement, description, toolTip);
          QuickFixAction.registerQuickFixAction(info, constructorCall.getTextRange(), new CreateConstructorFromCallAction(constructorCall), null, null);
          if (classReference != null) {
            CastConstructorParametersFix.registerCastActions(classReference, constructorCall, info, getFixRange(infoElement));
            ChangeMethodSignatureFromUsageFix.registerIntentions(results, list, info, null);
          }
          info.navigationShift = +1;
          return info;
        }
        else {
          HighlightInfo highlightInfo = GenericsHighlightUtil.checkUncheckedCall(result, constructorCall);
          if (highlightInfo != null) return highlightInfo;
          if (constructorCall instanceof PsiNewExpression) {
            highlightInfo = GenericsHighlightUtil.checkGenericCallWithRawArguments(result, (PsiCallExpression)constructorCall);
          }
          if (highlightInfo != null) return highlightInfo;
        }
      }
    }
    return null;
  }

  private static boolean callingProtectedConstructorFromDerivedClass(PsiMethod constructor, PsiConstructorCall place) {
    if (!constructor.hasModifierProperty(PsiModifier.PROTECTED)) return false;
    PsiClass constructorClass = constructor.getContainingClass();
    if (constructorClass == null) return false;
    // indirect instantiation via anonymous class is ok
    if (place instanceof PsiNewExpression && ((PsiNewExpression)place).getAnonymousClass() != null) return false;
    PsiElement curElement = place;
    while (true) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(curElement, PsiClass.class);
      if (aClass == null) return false;
      curElement = aClass;
      if (aClass.isInheritor(constructorClass, true) && !aClass.getManager().arePackagesTheSame(aClass, constructorClass)) {
        return true;
      }
    }
  }

  public static boolean isSerializationRelatedMethod(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null || method.isConstructor()) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    @NonNls String name = method.getName();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiType returnType = method.getReturnType();
    if ("readObjectNoData".equals(name)) {
      return parameters.length == 0 && TypeConversionUtil.isVoidType(returnType) && HighlightUtil.isSerializable(aClass);
    }
    if ("readObject".equals(name)) {
      return parameters.length == 1
             && parameters[0].getType().equalsToText("java.io.ObjectInputStream")
             && TypeConversionUtil.isVoidType(returnType) && method.hasModifierProperty(PsiModifier.PRIVATE)
             && HighlightUtil.isSerializable(aClass);
    }
    if ("readResolve".equals(name)) {
      return parameters.length == 0
             && returnType != null
             && returnType.equalsToText("java.lang.Object")
             && HighlightUtil.isSerializable(aClass);
    }
    if ("writeReplace".equals(name)) {
      return parameters.length == 0
             && returnType != null
             && returnType.equalsToText("java.lang.Object")
             && HighlightUtil.isSerializable(aClass);
    }
    if ("writeObject".equals(name)) {
      return parameters.length == 1
             && TypeConversionUtil.isVoidType(returnType)
             && parameters[0].getType().equalsToText("java.io.ObjectOutputStream")
             && method.hasModifierProperty(PsiModifier.PRIVATE)
             && HighlightUtil.isSerializable(aClass);
    }
    return false;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */

public class GenericsHighlightUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil");
  private static final String GENERICS_ARE_NOT_SUPPORTED = JavaErrorMessages.message("generics.are.not.supported");
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private GenericsHighlightUtil() {}

  public static HighlightInfo checkInferredTypeArguments(PsiMethod genericMethod,
                                                         PsiMethodCallExpression call,
                                                         PsiSubstitutor substitutor) {
    PsiTypeParameter[] typeParameters = genericMethod.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted == null) return null;
      substituted = PsiUtil.captureToplevelWildcards(substituted, call);
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      for (PsiClassType type : extendsTypes) {
        PsiType extendsType = substitutor.substitute(type);
        if (!TypeConversionUtil.isAssignable(extendsType, substituted, false)) {
          PsiClass boundClass = extendsType instanceof PsiClassType ? ((PsiClassType)extendsType).resolve() : null;

          @NonNls String messageKey = boundClass == null || typeParameter.isInterface() == boundClass.isInterface()
                                      ? "generics.inferred.type.for.type.parameter.is.not.within.its.bound.extend"
                                      : "generics.inferred.type.for.type.parameter.is.not.within.its.bound.implement";

          String description = JavaErrorMessages.message(
            messageKey,
            HighlightUtil.formatClass(typeParameter),
            HighlightUtil.formatType(extendsType),
            HighlightUtil.formatType(substituted)
          );

          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, call, description);
        }
      }
    }

    return null;
  }

  public static HighlightInfo checkParameterizedReferenceTypeArguments(PsiElement resolved,
                                                                       final PsiJavaCodeReferenceElement referenceElement,
                                                                       final PsiSubstitutor substitutor) {
    if (!(resolved instanceof PsiTypeParameterListOwner)) return null;
    final PsiTypeParameterListOwner typeParameterListOwner = (PsiTypeParameterListOwner)resolved;
    return checkReferenceTypeArgumentList(typeParameterListOwner, referenceElement.getParameterList(), substitutor, true);
  }

  public static HighlightInfo checkReferenceTypeArgumentList(final PsiTypeParameterListOwner typeParameterListOwner,
                                                             final PsiReferenceParameterList referenceParameterList,
                                                             final PsiSubstitutor substitutor,
                                                             boolean registerIntentions) {
    if (referenceParameterList != null && !PsiUtil.isLanguageLevel5OrHigher(referenceParameterList)) {
      if (referenceParameterList.getTypeParameterElements().length > 0) {
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, referenceParameterList, GENERICS_ARE_NOT_SUPPORTED);
        QuickFixAction.registerQuickFixAction(info, new ShowModulePropertiesFix(referenceParameterList));
        QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
        return info;
      }
    }

    PsiDiamondType.DiamondInferenceResult inferenceResult = null;
    PsiTypeElement[] referenceElements = null;
    if (referenceParameterList != null) {
      referenceElements = referenceParameterList.getTypeParameterElements();
      if (referenceElements.length == 1 && referenceElements[0].getType() instanceof PsiDiamondType) {
        if (!typeParameterListOwner.hasTypeParameters()) {
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, referenceElements[0], "Diamond operator is not applicable for non-parameterized types");
        }
        inferenceResult = ((PsiDiamondType)referenceElements[0].getType()).resolveInferredTypes();
        final PsiType expectedType = detectExpectedType(referenceParameterList);
        if (!(expectedType instanceof PsiClassType && ((PsiClassType)expectedType).isRaw())) {
          final String errorMessage = inferenceResult.getErrorMessage();
          if (errorMessage != null) {
           return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, referenceElements[0], errorMessage);
          }
        }
      }
    }

    final PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
    final int targetParametersNum = typeParameters.length;
    final int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeArguments().length;
    if (targetParametersNum != refParametersNum && refParametersNum != 0) {
      final String description;
      if (targetParametersNum == 0) {
        if (PsiTreeUtil.getParentOfType(referenceParameterList, PsiCall.class) != null &&
            PsiUtil.isLanguageLevel7OrHigher(referenceParameterList)) {
          description = null;
        } else {
          description = JavaErrorMessages.message(
            "generics.type.or.method.does.not.have.type.parameters",
            typeParameterListOwnerCategoryDescription(typeParameterListOwner),
            typeParameterListOwnerDescription(typeParameterListOwner)
          );
        }
      }
      else {
        description = JavaErrorMessages.message(
          "generics.wrong.number.of.type.arguments", refParametersNum, targetParametersNum
        );
      }

      if (description != null) {
        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, referenceParameterList, description);
        if (registerIntentions) {
          PsiElement pparent = referenceParameterList.getParent().getParent();
          if (pparent instanceof PsiTypeElement) {
            PsiElement variable = pparent.getParent();
            if (variable instanceof PsiVariable) {
              VariableParameterizedTypeFix.registerIntentions(highlightInfo, (PsiVariable)variable, referenceParameterList);
            }
          }
        }
        return highlightInfo;
      }
    }

    // bounds check
    if (targetParametersNum > 0 && refParametersNum != 0) {
      if (inferenceResult != null) {
        final PsiType[] types = inferenceResult.getTypes();
        for (int i = 0; i < typeParameters.length; i++) {
          final PsiType type = types[i];
          final HighlightInfo highlightInfo = checkTypeParameterWithinItsBound(typeParameters[i], substitutor,  type, referenceElements[0]);
          if (highlightInfo != null) return highlightInfo;
        }
      } else {
        for (int i = 0; i < typeParameters.length; i++) {
          final PsiTypeElement typeElement = referenceElements[i];
          final HighlightInfo highlightInfo = checkTypeParameterWithinItsBound(typeParameters[i], substitutor, typeElement.getType(), typeElement);
          if (highlightInfo != null) return highlightInfo;
        }
      }
    }

    return null;
  }

  private static PsiType detectExpectedType(PsiReferenceParameterList referenceParameterList) {
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(referenceParameterList, PsiNewExpression.class);
    LOG.assertTrue(newExpression != null);
    final PsiElement parent = newExpression.getParent();
    PsiType expectedType = null;
    if (parent instanceof PsiVariable && newExpression.equals(((PsiVariable)parent).getInitializer())) {
      expectedType = ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression && newExpression.equals(((PsiAssignmentExpression)parent).getRExpression())) {
      expectedType = ((PsiAssignmentExpression)parent).getLExpression().getType();
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        expectedType = method.getReturnType();
      }
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiElement pParent = parent.getParent();
      if (pParent instanceof PsiCallExpression && parent.equals(((PsiCallExpression)pParent).getArgumentList())) {
        final PsiMethod method = ((PsiCallExpression)pParent).resolveMethod();
        if (method != null) {
          final PsiExpression[] expressions = ((PsiCallExpression)pParent).getArgumentList().getExpressions();
          final int idx = ArrayUtil.find(expressions, newExpression);
          if (idx > -1) {
            final PsiParameterList parameterList = method.getParameterList();
            if (idx < parameterList.getParametersCount()) {
              expectedType = parameterList.getParameters()[idx].getType();
            }
          }
        }
      }
    }
    return expectedType;
  }

  @Nullable
  private static HighlightInfo checkTypeParameterWithinItsBound(final PsiTypeParameter classParameter,
                                                                final PsiSubstitutor substitutor,
                                                                final PsiType type,
                                                                final PsiElement typeElement2Highlight) {
    final PsiClass referenceClass;
    if (type instanceof PsiClassType){
      referenceClass = ((PsiClassType)type).resolve();
    } else {
      referenceClass = null;
    }
    final PsiType psiType = substitutor.substitute(classParameter);
    if (psiType instanceof PsiClassType && !(PsiUtil.resolveClassInType(psiType) instanceof PsiTypeParameter)) {
      if (checkNotInBounds(type, psiType)) {
        final String description = "Actual type argument and inferred type contradict each other";
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement2Highlight, description);
      }
    }

    final PsiClassType[] bounds = classParameter.getSuperTypes();
    for (PsiClassType type1 : bounds) {
      PsiType bound = substitutor.substitute(type1);
      if (checkNotInBounds(type, bound)) {
        PsiClass boundClass = bound instanceof PsiClassType ? ((PsiClassType)bound).resolve() : null;

        @NonNls final String messageKey = boundClass == null || referenceClass == null || referenceClass.isInterface() == boundClass.isInterface()
                                          ? "generics.type.parameter.is.not.within.its.bound.extend"
                                          : "generics.type.parameter.is.not.within.its.bound.implement";

        String description = JavaErrorMessages.message(messageKey,
                                                       referenceClass != null ? HighlightUtil.formatClass(referenceClass) : type.getPresentableText(),
                                                       HighlightUtil.formatType(bound));

        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                              typeElement2Highlight,
                                                                              description);
        if (bound instanceof PsiClassType && referenceClass != null) {
          QuickFixAction
            .registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createExtendsListFix(referenceClass, (PsiClassType)bound, true),
                                    null);
        }
        return highlightInfo;
      }
    }
    return null;
  }

  private static boolean checkNotInBounds(PsiType type, PsiType bound) {
    if (type instanceof PsiClassType) {
      return checkNotAssignable(bound, type);
    }
    else {
      if (type instanceof PsiWildcardType) {
        if (((PsiWildcardType)type).isExtends()) {
          return checkExtendsWildcardCaptureFailure((PsiWildcardType)type, bound);
        }
        else if (((PsiWildcardType)type).isSuper()) {
          return checkNotAssignable(bound, ((PsiWildcardType)type).getSuperBound());
        }
      }
      else if (type instanceof PsiArrayType) {
        return checkNotAssignable(bound, type, true);
      }
    }
    return false;
  }

  //JLS 5.1.10
  private static boolean checkExtendsWildcardCaptureFailure(PsiWildcardType type, PsiType bound) {
    LOG.assertTrue(type.isExtends());
    final PsiType extendsBound = type.getExtendsBound();
    PsiType boundBound = bound;
    if (bound instanceof PsiWildcardType) {
      if (((PsiWildcardType)bound).isBounded()) {
        boundBound = ((PsiWildcardType)bound).isSuper()
                     ? ((PsiWildcardType)bound).getSuperBound()
                     : ((PsiWildcardType)bound).getExtendsBound();
      } else {
        return false;
      }
    }
    return !TypeConversionUtil.areTypesConvertible(boundBound, extendsBound) &&
           !TypeConversionUtil.areTypesConvertible(extendsBound, boundBound);
  }

  private static boolean checkNotAssignable(final PsiType bound, final PsiType type) {
    return checkNotAssignable(bound, type, allowUncheckedConversions(type));
  }

  private static boolean checkNotAssignable(final PsiType bound,
                                            final PsiType type,
                                            final boolean allowUncheckedConversion) {
    if (bound instanceof PsiWildcardType) {
      if (((PsiWildcardType)bound).isBounded()) {
        final PsiType boundBound = ((PsiWildcardType)bound).isExtends()
                                   ? ((PsiWildcardType)bound).getExtendsBound()
                                   : ((PsiWildcardType)bound).getSuperBound();
        return !TypeConversionUtil.isAssignable(boundBound, type, allowUncheckedConversion);
      } else {
        return true;
      }
    } else {
      return !TypeConversionUtil.isAssignable(bound, type, allowUncheckedConversion);
    }
  }

  private static boolean allowUncheckedConversions(PsiType type) {
    boolean allowUncheckedConversions = true;
    if (type instanceof PsiClassType) {
      final PsiClass classType = ((PsiClassType)type).resolve();
      if (classType != null) {
        for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(classType)) {
          allowUncheckedConversions &=  parameter.getExtendsListTypes().length == 0;
        }
      }
    }
    return allowUncheckedConversions;
  }

  private static String typeParameterListOwnerDescription(final PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass) {
      return HighlightUtil.formatClass((PsiClass)typeParameterListOwner);
    }
    else if (typeParameterListOwner instanceof PsiMethod) {
      return HighlightUtil.formatMethod((PsiMethod)typeParameterListOwner);
    }
    else {
      LOG.error("Unknown " + typeParameterListOwner);
      return "?";
    }
  }

  private static String typeParameterListOwnerCategoryDescription(final PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass) {
      return JavaErrorMessages.message("generics.holder.type");
    }
    else if (typeParameterListOwner instanceof PsiMethod) {
      return JavaErrorMessages.message("generics.holder.method");
    }
    else {
      LOG.error("Unknown " + typeParameterListOwner);
      return "?";
    }
  }

  public static HighlightInfo checkElementInTypeParameterExtendsList(PsiReferenceList referenceList, JavaResolveResult resolveResult, PsiElement element) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    final PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom == null) return null;
    HighlightInfo errorResult = null;
    if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
      final String description = HighlightClassUtil.INTERFACE_EXPECTED;
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, description);
      PsiClassType type =
        JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, new MoveBoundClassToFrontFix(aClass, type), null);
    }
    else if (referenceElements.length != 0 && element != referenceElements[0] && referenceElements[0].resolve() instanceof PsiTypeParameter) {
      final String description = JavaErrorMessages.message("type.parameter.cannot.be.followed.by.other.bounds");
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, description);
      PsiClassType type =
        JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      IntentionAction fix = QUICK_FIX_FACTORY.createExtendsListFix(aClass, type, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix, null);
    }
    return errorResult;
  }
  public static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass) {
    if (aClass instanceof PsiTypeParameter) return null;
    final PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return null;
    Map<PsiClass, PsiSubstitutor> inheritedClasses = new HashMap<PsiClass, PsiSubstitutor>();
    final TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkInterfaceMultipleInheritance(aClass,
                                             PsiSubstitutor.EMPTY, inheritedClasses,
                                             new HashSet<PsiClass>(), textRange);
  }

  private static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass,
                                                                 PsiSubstitutor derivedSubstitutor,
                                                                 Map<PsiClass, PsiSubstitutor> inheritedClasses,
                                                                 Set<PsiClass> visited,
                                                                 TextRange textRange) {
    final PsiClassType[] superTypes = aClass.getSuperTypes();
    for (PsiClassType superType : superTypes) {
      final PsiClassType.ClassResolveResult result = superType.resolveGenerics();
      final PsiClass superClass = result.getElement();
      if (superClass == null || visited.contains(superClass)) continue;
      PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
      superTypeSubstitutor = MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, derivedSubstitutor);

      final PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
      if (inheritedSubstitutor != null) {
        final PsiTypeParameter[] typeParameters = superClass.getTypeParameters();
        for (PsiTypeParameter typeParameter : typeParameters) {
          PsiType type1 = inheritedSubstitutor.substitute(typeParameter);
          PsiType type2 = superTypeSubstitutor.substitute(typeParameter);

          if (!Comparing.equal(type1, type2)) {
            String description = JavaErrorMessages.message("generics.cannot.be.inherited.with.different.type.arguments",
                                                           HighlightUtil.formatClass(superClass),
                                                           HighlightUtil.formatType(type1),
                                                           HighlightUtil.formatType(type2));
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
          }
        }
      }
      inheritedClasses.put(superClass, superTypeSubstitutor);
      visited.add(superClass);
      final HighlightInfo highlightInfo = checkInterfaceMultipleInheritance(superClass, superTypeSubstitutor, inheritedClasses, visited, textRange);
      visited.remove(superClass);

      if (highlightInfo != null) return highlightInfo;
    }
    return null;
  }

  public static HighlightInfo checkOverrideEquivalentMethods(final PsiClass aClass) {
    final Collection<HierarchicalMethodSignature> signaturesWithSupers = aClass.getVisibleSignatures();
    PsiManager manager = aClass.getManager();
    Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods =
      new THashMap<MethodSignature, MethodSignatureBackedByPsiMethod>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
                                                                       
    for (HierarchicalMethodSignature signature : signaturesWithSupers) {
      HighlightInfo info = checkSameErasureNotSubsignatureInner(signature, manager, aClass, sameErasureMethods);
      if (info != null) return info;
    }

    return null;
  }

  private static HighlightInfo checkSameErasureNotSubsignatureInner(final HierarchicalMethodSignature signature,
                                                                    final PsiManager manager,
                                                                    final PsiClass aClass,
                                                                    final Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods) {
    PsiMethod method = signature.getMethod();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (!facade.getResolveHelper().isAccessible(method, aClass, null)) return null;
    MethodSignature signatureToErase = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignatureBackedByPsiMethod sameErasure = sameErasureMethods.get(signatureToErase);
    HighlightInfo info;
    if (sameErasure != null) {
      info = checkSameErasureNotSubsignatureOrSameClass(sameErasure, signature, aClass, method);
      if (info != null) return info;
    }
    sameErasureMethods.put(signatureToErase, signature);
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : supers) {
      info = checkSameErasureNotSubsignatureInner(superSignature, manager, aClass, sameErasureMethods);
      if (info != null) return info;
    }
    return null;
  }

  private static HighlightInfo checkSameErasureNotSubsignatureOrSameClass(final MethodSignatureBackedByPsiMethod signatureToCheck,
                                                                          final HierarchicalMethodSignature superSignature,
                                                                          final PsiClass aClass,
                                                                          final PsiMethod superMethod) {
    final PsiMethod checkMethod = signatureToCheck.getMethod();
    if (superMethod.equals(checkMethod)) return null;
    PsiClass checkContainingClass = checkMethod.getContainingClass();
    LOG.assertTrue(checkContainingClass != null);
    PsiClass superContainingClass = superMethod.getContainingClass();
    boolean checkEqualsSuper = checkContainingClass.equals(superContainingClass);
    if (checkMethod.isConstructor()) {
      if (!superMethod.isConstructor() || !checkEqualsSuper) return null;
    }
    else if (superMethod.isConstructor()) return null;

    if (checkMethod.hasModifierProperty(PsiModifier.STATIC) && !checkEqualsSuper) {
      return null;
    }

    final PsiType retErasure1 = TypeConversionUtil.erasure(checkMethod.getReturnType());
    final PsiType retErasure2 = TypeConversionUtil.erasure(superMethod.getReturnType());
    if (!Comparing.equal(retErasure1, retErasure2) &&
        !TypeConversionUtil.isVoidType(retErasure1) &&
        !TypeConversionUtil.isVoidType(retErasure2) &&
        !(checkEqualsSuper && Arrays.equals(superSignature.getParameterTypes(), signatureToCheck.getParameterTypes()))) {
      return null;
    }

    if (!checkEqualsSuper && MethodSignatureUtil.isSubsignature(superSignature, signatureToCheck)) {
      return null;
    }
    if (superContainingClass != null && !superContainingClass.isInterface() && checkContainingClass.isInterface() && !aClass.equals(superContainingClass)) return null;
    if (aClass.equals(checkContainingClass)) {
      boolean sameClass = aClass.equals(superContainingClass);
      return getSameErasureMessage(sameClass, checkMethod, superMethod, HighlightNamesUtil.getMethodDeclarationTextRange(checkMethod));
    }
    else {
      return getSameErasureMessage(false, checkMethod, superMethod, HighlightNamesUtil.getClassDeclarationTextRange(aClass));
    }
  }

  private static HighlightInfo getSameErasureMessage(final boolean sameClass, final PsiMethod method, final PsiMethod superMethod,
                                                     TextRange textRange) {
    @NonNls final String key = sameClass ? "generics.methods.have.same.erasure" : "generics.methods.have.same.erasure.override";
    String description = JavaErrorMessages.message(key, HighlightMethodUtil.createClashMethodMessage(method, superMethod, !sameClass));
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
  }

  public static HighlightInfo checkTypeParameterInstantiation(PsiNewExpression expression) {
    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference == null) return null;
    final JavaResolveResult result = classReference.advancedResolve(false);
    final PsiElement element = result.getElement();
    if (element instanceof PsiTypeParameter) {
      String description = JavaErrorMessages.message("generics.type.parameter.cannot.be.instantiated",
                                                     HighlightUtil.formatClass((PsiTypeParameter)element));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, classReference, description);
    }
    return null;
  }

  public static HighlightInfo checkWildcardUsage(PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type instanceof PsiWildcardType) {
      if (typeElement.getParent() instanceof PsiReferenceParameterList) {
        PsiElement parent = typeElement.getParent().getParent();
        LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement, parent);
        PsiElement refParent = parent.getParent();
        if (refParent instanceof PsiAnonymousClass) refParent = refParent.getParent();
        if (refParent instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)refParent;
          if (!(newExpression.getType() instanceof PsiArrayType)) {
            String description = JavaErrorMessages.message("wildcard.type.cannot.be.instantiated", HighlightUtil.formatType(type));
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, description);
          }
        }
        else if (refParent instanceof PsiReferenceList) {
          PsiElement refPParent = refParent.getParent();
          if (!(refPParent instanceof PsiTypeParameter) || refParent != ((PsiTypeParameter)refPParent).getExtendsList()) {
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     typeElement,
                                                     JavaErrorMessages.message("generics.wildcard.not.expected"));
          }
        }
      }
      else {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 typeElement,
                                                 JavaErrorMessages.message("generics.wildcards.may.be.used.only.as.reference.parameters"));
      }
    }

    return null;
  }

  public static HighlightInfo checkReferenceTypeUsedAsTypeArgument(PsiTypeElement typeElement) {
    final PsiType type = typeElement.getType();
    if (type != PsiType.NULL && type instanceof PsiPrimitiveType ||
        type instanceof PsiWildcardType && ((PsiWildcardType)type).getBound() instanceof PsiPrimitiveType) {
      final PsiElement element = new PsiMatcherImpl(typeElement)
        .parent(PsiMatchers.hasClass(PsiReferenceParameterList.class))
        .parent(PsiMatchers.hasClass(PsiJavaCodeReferenceElement.class))
        .getElement();
      if (element == null) return null;

      String description = JavaErrorMessages.message("generics.type.argument.cannot.be.of.primitive.type");
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, description);
    }

    return null;
  }

  public static boolean isRawToGeneric(PsiType lType, PsiType rType) {
    if (lType instanceof PsiPrimitiveType || rType instanceof PsiPrimitiveType) return false;
    if (lType.equals(rType)) return false;
    if (lType instanceof PsiArrayType && rType instanceof PsiArrayType) {
      return isRawToGeneric(((PsiArrayType)lType).getComponentType(), ((PsiArrayType)rType).getComponentType());
    }
    if (lType instanceof PsiArrayType || rType instanceof PsiArrayType) return false;

    if (rType instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)rType).getConjuncts()) {
        if (isRawToGeneric(lType, type)) return true;
      }
      return false;
    } else if (lType instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)lType).getConjuncts()) {
        if (isRawToGeneric(type, rType)) return true;
      }
      return false;
    }

    if (rType instanceof PsiDisjunctionType || lType instanceof PsiDisjunctionType) {
      return false;
    }

    if (lType instanceof PsiCapturedWildcardType || rType instanceof PsiCapturedWildcardType) {
      return false;
    }

    if (lType instanceof PsiWildcardType || rType instanceof PsiWildcardType) return false;

    boolean isValidType = lType instanceof PsiClassType && rType instanceof PsiClassType;
    if (!isValidType) {
      LOG.error("Invalid types: rType =" + rType + ", lType=" + lType);
    }
    PsiClassType.ClassResolveResult lResolveResult = ((PsiClassType)lType).resolveGenerics();
    PsiClassType.ClassResolveResult rResolveResult = ((PsiClassType)rType).resolveGenerics();
    PsiClass lClass = lResolveResult.getElement();
    PsiClass rClass = rResolveResult.getElement();

    if (rClass instanceof PsiAnonymousClass) {
      return isRawToGeneric(lType, ((PsiAnonymousClass)rClass).getBaseClassType());
    }

    PsiSubstitutor lSubstitutor = lResolveResult.getSubstitutor();
    PsiSubstitutor rSubstitutor = rResolveResult.getSubstitutor();
    if (lClass == null || rClass == null) return false;
    if (lClass instanceof PsiTypeParameter &&
        !InheritanceUtil.isInheritorOrSelf(rClass, lClass, true)) return true;

    if (!lClass.getManager().areElementsEquivalent(lClass, rClass)) {
      if (lClass.isInheritor(rClass, true)) {
        lSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(rClass, lClass, lSubstitutor);
        lClass = rClass;
      }
      else if (rClass.isInheritor(lClass, true)) {
        rSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(lClass, rClass, rSubstitutor);
        rClass = lClass;
      }
      else {
        return false;
      }
    }

    Iterator<PsiTypeParameter> lIterator = PsiUtil.typeParametersIterator(lClass);
    Iterator<PsiTypeParameter> rIterator = PsiUtil.typeParametersIterator(rClass);
    while (lIterator.hasNext()) {
      if (!rIterator.hasNext()) return false;
      PsiTypeParameter lParameter = lIterator.next();
      PsiTypeParameter rParameter = rIterator.next();
      PsiType lTypeArg = lSubstitutor.substitute(lParameter);
      PsiType rTypeArg = rSubstitutor.substituteWithBoundsPromotion(rParameter);
      if (lTypeArg == null) continue;
      if (rTypeArg == null) {
        if (lTypeArg instanceof PsiWildcardType && ((PsiWildcardType) lTypeArg).getBound() == null) {
          continue;
        }
        else {
          return true;
        }
      }
      if (isUncheckedTypeArgumentConversion(lTypeArg, rTypeArg)) return true;
    }
    return false;
  }

  public static boolean isUncheckedCast(PsiType castType, PsiType operandType) {
    if (TypeConversionUtil.isAssignable(castType, operandType, false)) return false;

    castType = castType.getDeepComponentType();
    if (castType instanceof PsiClassType) {
      final PsiClassType castClassType = (PsiClassType)castType;
      operandType = operandType.getDeepComponentType();

      if (!(operandType instanceof PsiClassType)) return false;
      final PsiClassType operandClassType = (PsiClassType)operandType;
      final PsiClassType.ClassResolveResult castResult = castClassType.resolveGenerics();
      final PsiClassType.ClassResolveResult operandResult = operandClassType.resolveGenerics();
      final PsiClass operandClass = operandResult.getElement();
      final PsiClass castClass = castResult.getElement();

      if (operandClass == null || castClass == null) return false;
      if (castClass instanceof PsiTypeParameter) return true;

      if (castClassType.hasNonTrivialParameters()) {
        if (operandClassType.isRaw()) return true;
        if (castClass.isInheritor(operandClass, true)) {
          PsiSubstitutor castSubstitutor = castResult.getSubstitutor();
          for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(castClass)) {
            PsiSubstitutor modifiedSubstitutor = castSubstitutor.put(typeParameter, null);
            PsiClassType otherType =
              JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(castClass, modifiedSubstitutor);
            if (TypeConversionUtil.isAssignable(operandType, otherType, false)) return true;
          }
          return false;
        }
        return true;
      }
    }

    return false;
  }

  private static boolean isUncheckedTypeArgumentConversion (PsiType lTypeArg, PsiType rTypeArg) {
    if (lTypeArg instanceof PsiPrimitiveType || rTypeArg instanceof PsiPrimitiveType) return false;
    if (lTypeArg.equals(rTypeArg)) return false;
    if (lTypeArg instanceof PsiCapturedWildcardType) {
      //ignore capture conversion
      return isUncheckedTypeArgumentConversion(((PsiCapturedWildcardType)lTypeArg).getWildcard(), rTypeArg);
    }
    if (rTypeArg instanceof PsiCapturedWildcardType) {
      //ignore capture conversion
      return isUncheckedTypeArgumentConversion(lTypeArg, ((PsiCapturedWildcardType)rTypeArg).getWildcard());
    }

    if (lTypeArg instanceof PsiWildcardType || rTypeArg instanceof PsiWildcardType) {
      return !lTypeArg.isAssignableFrom(rTypeArg);
    }

    if (lTypeArg instanceof PsiArrayType && rTypeArg instanceof PsiArrayType) {
      return isUncheckedTypeArgumentConversion(((PsiArrayType)rTypeArg).getComponentType(), ((PsiArrayType)lTypeArg).getComponentType());
    }
    if (lTypeArg instanceof PsiArrayType || rTypeArg instanceof PsiArrayType) return false;
    if (lTypeArg instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)lTypeArg).getConjuncts()) {
        if (!isUncheckedTypeArgumentConversion(type, rTypeArg)) return false;
      }
      return true;
    }
    if (!(lTypeArg instanceof PsiClassType)) {
      LOG.error("left: "+lTypeArg + "; "+lTypeArg.getClass());
    }
    if (rTypeArg instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)rTypeArg).getConjuncts()) {
        if (!isUncheckedTypeArgumentConversion(lTypeArg, type)) return false;
      }
      return true;
    }
    if (!(rTypeArg instanceof PsiClassType)) {
      LOG.error("right :"+rTypeArg + "; "+rTypeArg.getClass());
    }
    return ((PsiClassType)lTypeArg).resolve() instanceof PsiTypeParameter ||
           ((PsiClassType)rTypeArg).resolve() instanceof PsiTypeParameter;
  }

  @Nullable
  public static HighlightInfo checkForeachLoopParameterType(PsiForeachStatement statement) {
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression expression = statement.getIteratedValue();
    if (expression == null || expression.getType() == null) return null;
    final PsiType itemType = getCollectionItemType(expression);
    if (itemType == null) {
      String description = JavaErrorMessages.message("foreach.not.applicable",
                                                     HighlightUtil.formatType(expression.getType()));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, description);
    }
    final int start = parameter.getTextRange().getStartOffset();
    final int end = expression.getTextRange().getEndOffset();
    final PsiType parameterType = parameter.getType();
    HighlightInfo highlightInfo = HighlightUtil.checkAssignability(parameterType, itemType, null, new TextRange(start, end));
    if (highlightInfo != null) {
      HighlightUtil.registerChangeVariableTypeFixes(parameter, itemType, highlightInfo);
    }
    return highlightInfo;
  }

  @Nullable
  public static PsiType getCollectionItemType(@NotNull PsiExpression expression) {
    final PsiType type = expression.getType();
    if (type == null) return null;
    if (type instanceof PsiArrayType) {
      return ((PsiArrayType)type).getComponentType();
    }
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return null;
      final PsiManager manager = aClass.getManager();
      final String qName = aClass.getQualifiedName();
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      if (qName != null) {
        PsiClass myClass = facade.findClass(qName, expression.getResolveScope());
        if (myClass != null && myClass != aClass) {
          //different JDKs
          PsiTypeParameter thisTypeParameter = getIterableTypeParameter(facade, myClass);
          if (thisTypeParameter == null) return null;
          PsiTypeParameter thatTypeParameter = getIterableTypeParameter(facade, aClass);
          if (thatTypeParameter != null) { //it can be null if we reference collection in JDK1.4 module from JDK5 source
            substitutor = substitutor.put(thisTypeParameter, substitutor.substitute(thatTypeParameter));
          }
          aClass = myClass;
        }
      }
      PsiTypeParameter typeParameter = getIterableTypeParameter(facade, aClass);
      if (typeParameter == null) return null;
      PsiClass owner = (PsiClass)typeParameter.getOwner();
      if (owner == null) return null;
      PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getClassSubstitutor(owner, aClass, PsiSubstitutor.EMPTY);
      if (superClassSubstitutor == null) return null;
      PsiType itemType = superClassSubstitutor.substitute(typeParameter);
      itemType = substitutor.substitute(itemType);
      return itemType == null ? PsiType.getJavaLangObject(manager, aClass.getResolveScope()) : itemType;
    }
    return null;
  }

  @Nullable
  private static PsiTypeParameter getIterableTypeParameter(final JavaPsiFacade facade, final PsiClass context) {
    PsiClass iterable = facade.findClass("java.lang.Iterable", context.getResolveScope());
    if (iterable == null) return null;
    PsiTypeParameter[] typeParameters = iterable.getTypeParameters();
    if (typeParameters.length != 1) return null;
    return typeParameters[0];
  }

  @Nullable
  public static HighlightInfo checkAccessStaticFieldFromEnumConstructor(PsiReferenceExpression expr, JavaResolveResult result) {
    final PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiField)) return null;
    if (!((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiMember constructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
    if (constructorOrInitializer == null) return null;
    if (constructorOrInitializer.hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiClass aClass = constructorOrInitializer.getContainingClass();
    if (aClass == null) return null;
    if (!aClass.isEnum()) return null;
    final PsiField field = (PsiField)resolved;
    if (field.getContainingClass() != aClass) return null;
    final PsiType type = field.getType();

    //TODO is access to enum constant is allowed ?
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) return null;

    if (PsiUtil.isCompileTimeConstant(field)) return null;

    String description = JavaErrorMessages.message(
      "illegal.to.access.static.member.from.enum.constructor.or.instance.initializer",
      HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor())
    );

    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expr, description);
  }

  @Nullable
  public static HighlightInfo checkEnumInstantiation(PsiNewExpression expression) {
    final PsiType type = expression.getType();
    if (type instanceof PsiClassType) {
      final PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass != null && aClass.isEnum()) {
        String description = JavaErrorMessages.message("enum.types.cannot.be.instantiated");
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, description);
      }
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkGenericArrayCreation(PsiElement element, PsiType type) {
    if (type instanceof PsiArrayType) {
      PsiType componentType = type.getDeepComponentType();
      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)componentType;
        PsiType[] parameters = classType.getParameters();
        for (PsiType parameter : parameters) {
          if (!(parameter instanceof PsiWildcardType) || ((PsiWildcardType)parameter).getBound() != null) {
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                     element,
                                                     JavaErrorMessages.message("generic.array.creation"));
          }
        }
        final PsiClass resolved = ((PsiClassType)PsiUtil.convertAnonymousToBaseType(classType)).resolve();
        if (resolved instanceof PsiTypeParameter) {
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                   element,
                                                   JavaErrorMessages.message("generic.array.creation"));
        }
      }
    }

    return null;
  }

  private static final MethodSignature ourValuesEnumSyntheticMethod = MethodSignatureUtil.createMethodSignature("values",
                                                                                                                PsiType.EMPTY_ARRAY,
                                                                                                                PsiTypeParameter.EMPTY_ARRAY,
                                                                                                                PsiSubstitutor.EMPTY);

  public static boolean isEnumSyntheticMethod(MethodSignature methodSignature, Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    final PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    final MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString}, PsiTypeParameter.EMPTY_ARRAY,
                                                                                    PsiSubstitutor.EMPTY);
    return valueOfMethod.equals(methodSignature);
  }

  public static HighlightInfo checkTypeParametersList(PsiTypeParameterList parameterList) {
    PsiTypeParameter[] typeParameters = parameterList.getTypeParameters();
    if (typeParameters.length == 0) return null;
    if (!PsiUtil.isLanguageLevel5OrHigher(parameterList)) {
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameterList, GENERICS_ARE_NOT_SUPPORTED);
      QuickFixAction.registerQuickFixAction(info, new ShowModulePropertiesFix(parameterList));
      QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
      return info;
    }
    final PsiElement parent = parameterList.getParent();
    if (parent instanceof PsiClass && ((PsiClass)parent).isEnum()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               parameterList,
                                               JavaErrorMessages.message("generics.enum.may.not.have.type.parameters"));
    }
    if (parent instanceof PsiAnnotationMethod) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameterList, JavaErrorMessages.message("generics.annotation.members.may.not.have.type.parameters"));
    }
    else if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameterList, JavaErrorMessages.message("annotation.may.not.have.type.parameters"));
    }

    for (int i = 0; i < typeParameters.length; i++) {
      final PsiTypeParameter typeParameter1 = typeParameters[i];
      String name1 = typeParameter1.getName();
      for (int j = i+1; j < typeParameters.length; j++) {
        final PsiTypeParameter typeParameter2 = typeParameters[j];
        String name2 = typeParameter2.getName();
        if (Comparing.strEqual(name1, name2)) {
          String message = JavaErrorMessages.message("generics.duplicate.type.parameter", name1);
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeParameter2, message);
        }
      }
    }
    return null;
  }

  public static HighlightInfo checkCatchParameterIsClass(PsiParameter parameter) {
    if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
      PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)type).resolve();
        if (aClass instanceof PsiTypeParameter) {
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                   parameter.getTypeElement(),
                                                   JavaErrorMessages.message("generics.cannot.catch.type.parameters"));
        }
      }
    }

    return null;
  }

  public static HighlightInfo checkInstanceOfGenericType(PsiInstanceOfExpression expression) {
    final PsiTypeElement checkTypeElement = expression.getCheckType();
    if (checkTypeElement == null) return null;
    PsiElement ref = checkTypeElement.getInnermostComponentReferenceElement();
    while (ref instanceof PsiJavaCodeReferenceElement) {
      final HighlightInfo result = isIllegalForInstanceOf((PsiJavaCodeReferenceElement)ref, checkTypeElement);
      if (result != null) return result;
      ref = ((PsiQualifiedReference)ref).getQualifier();
    }
    return null;
  }

  private static HighlightInfo isIllegalForInstanceOf(PsiJavaCodeReferenceElement ref, final PsiTypeElement typeElement) {
    final PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiTypeParameter) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref, JavaErrorMessages.message("generics.cannot.instanceof.type.parameters"));
    }

    final PsiType[] parameters = ref.getTypeParameters();
    for (PsiType parameterType : parameters) {
      if (parameterType != null &&
          !(parameterType instanceof PsiWildcardType && ((PsiWildcardType)parameterType).getBound() == null)) {
         return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, JavaErrorMessages.message("illegal.generic.type.for.instanceof"));
      }
    }

    return null;
  }

  public static HighlightInfo checkClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    PsiType type = expression.getOperand().getType();
    if (type instanceof PsiClassType) {
      PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass instanceof PsiTypeParameter) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 expression.getOperand(),
                                                 JavaErrorMessages.message("cannot.select.dot.class.from.type.variable"));
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkOverrideAnnotation(PsiMethod method) {
    PsiModifierList list = method.getModifierList();
    final PsiAnnotation overrideAnnotation = list.findAnnotation("java.lang.Override");
    if (overrideAnnotation == null) {
      return null;
    }
    try {
      MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superMethod == null) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, overrideAnnotation,
                                                 JavaErrorMessages.message("method.doesnot.override.super"));
      }
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(method);
      PsiClass superClass = superMethod.getMethod().getContainingClass();
      if (languageLevel.equals(LanguageLevel.JDK_1_5) &&
          superClass != null &&
          superClass.isInterface()) {
        HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, overrideAnnotation, JavaErrorMessages.message("override.not.allowed.in.interfaces"));
        QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_6));
        return info;
      }
      return null;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Nullable
  public static HighlightInfo checkSafeVarargsAnnotation(PsiMethod method) {
    PsiModifierList list = method.getModifierList();
    final PsiAnnotation safeVarargsAnnotation = list.findAnnotation("java.lang.SafeVarargs");
    if (safeVarargsAnnotation == null) {
      return null;
    }
    try {
      if (!method.isVarArgs()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, safeVarargsAnnotation, "@SafeVarargs is not allowed on methods with fixed arity");
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC) && !method.hasModifierProperty(PsiModifier.FINAL) && !method.isConstructor()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, safeVarargsAnnotation, "@SafeVarargs is not allowed on non-final instance methods");
      }

      final PsiParameter varParameter = method.getParameterList().getParameters()[method.getParameterList().getParametersCount() - 1];

      for (PsiReference reference : ReferencesSearch.search(varParameter)) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiExpression && !PsiUtil.isAccessedForReading((PsiExpression)element)) {
          return HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING, element, "@SafeVarargs do not suppress potentially unsafe operations");
        }
      }



      LOG.assertTrue(varParameter.isVarArgs());
      final PsiEllipsisType ellipsisType = (PsiEllipsisType)varParameter.getType();
      final PsiType componentType = ellipsisType.getComponentType();
      if (isReifiableType(componentType)) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING, varParameter.getTypeElement(), "@SafeVarargs is not applicable for reifiable types");
      }
      return null;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  public static boolean isUncheckedWarning(PsiJavaCodeReferenceElement expression, PsiElement resolve) {
    if (resolve instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)resolve;

      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);

      if (psiMethod.isVarArgs()) {
        if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_7) || !AnnotationUtil.isAnnotated(psiMethod, "java.lang.SafeVarargs", false)) {
          final int parametersCount = psiMethod.getParameterList().getParametersCount();
          final PsiParameter varargParameter =
            psiMethod.getParameterList().getParameters()[parametersCount - 1];
          final PsiType componentType = ((PsiEllipsisType)varargParameter.getType()).getComponentType();
          if (!isReifiableType(componentType)) {
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiCall) {
              final PsiExpressionList argumentList = ((PsiCall)parent).getArgumentList();
              if (argumentList != null) {
                final PsiExpression[] args = argumentList.getExpressions();
                if (args.length == parametersCount) {
                  final PsiExpression lastArg = args[args.length - 1];
                  if (lastArg instanceof PsiReferenceExpression) {
                    final PsiElement lastArgsResolve = ((PsiReferenceExpression)lastArg).resolve();
                    if (lastArgsResolve instanceof PsiParameter) {
                      if (((PsiParameter)lastArgsResolve).getType() instanceof PsiArrayType) {
                        return false;
                      }
                    }
                  } else if (lastArg instanceof PsiMethodCallExpression) {
                    if (lastArg.getType() instanceof PsiArrayType) {
                      return false;
                    }
                  }
                }
                for (int i = parametersCount - 1; i < args.length; i++) {
                  if (!isReifiableType(args[i].getType())){
                    return true;
                  }
                }
                return args.length < parametersCount;
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static boolean isReifiableType(PsiType type) {
    if (type instanceof PsiArrayType) {
      return isReifiableType(((PsiArrayType)type).getComponentType());
    }

    if (type instanceof PsiPrimitiveType) {
      return true;
    }

    if (PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter) {
      return false;
    }

    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)PsiUtil.convertAnonymousToBaseType(type);
      if (classType.isRaw()) {
        return true;
      }
      PsiType[] parameters = classType.getParameters();

      for (PsiType parameter : parameters) {
        if (parameter instanceof PsiWildcardType && ((PsiWildcardType)parameter).getBound() == null) {
          return true;
        }
      }
      final PsiClass resolved = ((PsiClassType)PsiUtil.convertAnonymousToBaseType(classType)).resolve();
      if (resolved instanceof PsiTypeParameter) {
        return false;
      }
      return parameters.length == 0;
    }

    return false;
  }

  static void checkEnumConstantForConstructorProblems(PsiEnumConstant enumConstant, final HighlightInfoHolder holder) {
    PsiClass containingClass = enumConstant.getContainingClass();                      if (enumConstant.getInitializingClass() == null) {
      HighlightInfo highlightInfo = HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, enumConstant.getNameIdentifier());
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createImplementMethodsFix(enumConstant));
        holder.add(highlightInfo);
        return;
      }
      highlightInfo = HighlightClassUtil.checkClassWithAbstractMethods(enumConstant.getContainingClass(), enumConstant.getNameIdentifier().getTextRange());
      if (highlightInfo != null) {
        holder.add(highlightInfo);
        return;
      }
    }
    PsiClassType type = JavaPsiFacade.getInstance(enumConstant.getProject()).getElementFactory().createType(containingClass);

    HighlightMethodUtil.checkConstructorCall(type.resolveGenerics(), enumConstant, type, null, holder);
  }

  public static HighlightInfo checkEnumSuperConstructorCall(PsiMethodCallExpression expr) {
    PsiReferenceExpression methodExpression = expr.getMethodExpression();
    final PsiElement refNameElement = methodExpression.getReferenceNameElement();
    if (refNameElement != null && PsiKeyword.SUPER.equals(refNameElement.getText())) {
      final PsiMember constructor = PsiUtil.findEnclosingConstructorOrInitializer(expr);
      if (constructor instanceof PsiMethod && constructor.getContainingClass().isEnum()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 expr,
                                                 JavaErrorMessages.message("call.to.super.is.not.allowed.in.enum.constructor"));
      }
    }
    return null;
  }

  public static HighlightInfo checkVarArgParameterIsLast(PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiParameter[] params = ((PsiMethod)declarationScope).getParameterList().getParameters();
      if (parameter.isVarArgs()) {
        if (!PsiUtil.getLanguageLevel(parameter).hasEnumKeywordAndAutoboxing()) {
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameter, JavaErrorMessages.message("varargs.prior.15"));
          QuickFixAction.registerQuickFixAction(info, new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
          return info;
        }

        if (params[params.length - 1] != parameter) {
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameter,
                                                                 JavaErrorMessages.message("vararg.not.last.parameter"));
          QuickFixAction.registerQuickFixAction(info, new MakeVarargParameterLastFix(parameter), null);
          return info;
        }
      }
    }
    return null;
  }

  public static List<HighlightInfo> checkEnumConstantModifierList(PsiModifierList modifierList) {
    List<HighlightInfo> list = null;
    PsiElement[] children = modifierList.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiKeyword) {
        if (list == null) {
          list = new ArrayList<HighlightInfo>();
        }
        list.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                   child,
                                                   JavaErrorMessages.message("modifiers.for.enum.constants")));
      }
    }
    return list;
  }

  public static HighlightInfo checkParametersOnRaw(PsiReferenceParameterList refParamList) {
    if (refParamList.getTypeArguments().length == 0) return null;
    JavaResolveResult resolveResult = null;
    PsiElement parent = refParamList.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement) {
      resolveResult = ((PsiJavaCodeReferenceElement)parent).advancedResolve(false);
    } else if (parent instanceof PsiCallExpression) {
      resolveResult =  ((PsiCallExpression)parent).resolveMethodGenerics();
    }
    if (resolveResult != null) {
      PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiTypeParameterListOwner)) return null;
      if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) return null;
      PsiClass containingClass = ((PsiMember)element).getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, resolveResult.getSubstitutor())) {
        final String message = element instanceof PsiClass
                               ? JavaErrorMessages.message("generics.type.arguments.on.raw.type")
                               : JavaErrorMessages.message("generics.type.arguments.on.raw.method");

        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, refParamList, message);
      }
    }
    return null;
  }

  public static HighlightInfo checkCannotInheritFromEnum(PsiClass superClass, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (Comparing.strEqual("java.lang.Enum",superClass.getQualifiedName())) {
      String message = JavaErrorMessages.message("classes.extends.enum");
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, message);
    }
    return errorResult;
  }
  public static HighlightInfo checkGenericCannotExtendException(PsiReferenceList list) {
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass)) return null;
    PsiClass aClass = (PsiClass)parent;

    if (!aClass.hasTypeParameters() || aClass.getExtendsList() != list) return null;
    PsiJavaCodeReferenceElement[] referenceElements = list.getReferenceElements();
    PsiClass throwableClass = null;
    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
      PsiElement resolved = referenceElement.resolve();
      if (!(resolved instanceof PsiClass)) continue;
      if (throwableClass == null) {
        throwableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.lang.Throwable", aClass.getResolveScope());
      }
      if (InheritanceUtil.isInheritorOrSelf((PsiClass)resolved, throwableClass, true)) {
        String message = JavaErrorMessages.message("generic.extend.exception");
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, referenceElement, message);
        PsiClassType classType = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType((PsiClass)resolved);
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createExtendsListFix(aClass, classType, false));
        return highlightInfo;
      }
    }
    return null;
  }

  public static HighlightInfo checkEnumMustNotBeLocal(final PsiClass aClass) {
    if (!aClass.isEnum()) return null;
    PsiElement parent = aClass.getParent();
    if (!(parent instanceof PsiClass || parent instanceof PsiFile)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               HighlightNamesUtil.getClassDeclarationTextRange(aClass),
                                               JavaErrorMessages.message("local.enum"));
    }
    return null;
  }

  public static HighlightInfo checkSelectStaticClassFromParameterizedType(final PsiElement resolved, final PsiJavaCodeReferenceElement ref) {
    if (resolved instanceof PsiClass && ((PsiClass)resolved).hasModifierProperty(PsiModifier.STATIC)) {
      final PsiElement qualifier = ref.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiReferenceParameterList parameterList = ((PsiJavaCodeReferenceElement)qualifier).getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          final String message = JavaErrorMessages.message("generics.select.static.class.from.parameterized.type",
                                                           HighlightUtil.formatClass((PsiClass)resolved));
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parameterList, message);
        }
      }
    }
    return null;
  }

  public static HighlightInfo checkCannotInheritFromTypeParameter(final PsiClass superClass, final PsiJavaCodeReferenceElement toHighlight) {
    if (superClass instanceof PsiTypeParameter) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, toHighlight,
                                               JavaErrorMessages.message("class.cannot.inherit.from.its.type.parameter"));
    }
    return null;
  }
}


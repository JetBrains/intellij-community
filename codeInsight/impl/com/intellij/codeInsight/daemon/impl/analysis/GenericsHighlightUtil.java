package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      for (PsiClassType type : extendsTypes) {
        PsiType extendsType = substitutor.substituteAndFullCapture(type);
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
    return checkReferenceTypeParametersList(typeParameterListOwner, referenceElement, substitutor, true);
  }

  public static HighlightInfo checkReferenceTypeParametersList(final PsiTypeParameterListOwner typeParameterListOwner,
                                                               final PsiJavaCodeReferenceElement referenceElement,
                                                               final PsiSubstitutor substitutor, boolean registerIntentions) {
    if (PsiUtil.getLanguageLevel(referenceElement).compareTo(LanguageLevel.JDK_1_5) < 0) {
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 parameterList,
                                                 GENERICS_ARE_NOT_SUPPORTED);
      }
    }

    final PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
    final int targetParametersNum = typeParameters.length;
    final PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
    final int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeParameterElements().length;
    if (targetParametersNum != refParametersNum && refParametersNum != 0) {
      final String description;
      if (targetParametersNum == 0) {
        description = JavaErrorMessages.message(
          "generics.type.or.method.does.not.have.type.parameters",
          typeParameterListOwnerCategoryDescription(typeParameterListOwner),
          typeParameterListOwnerDescription(typeParameterListOwner)
        );
      }
      else {
        description = JavaErrorMessages.message(
          "generics.wrong.number.of.type.arguments", refParametersNum, targetParametersNum
        );
      }

      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            referenceParameterList,
                                                                            description);
      if (registerIntentions) {
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiTypeElement) {
          PsiElement variable = parent.getParent();
          if (variable instanceof PsiVariable) {
            VariableParameterizedTypeFix.registerIntentions(highlightInfo, (PsiVariable)variable, referenceElement);
          }
        }
      }
      return highlightInfo;
    }

    // bounds check
    if (targetParametersNum > 0 && refParametersNum != 0) {
      final PsiTypeElement[] referenceElements = referenceParameterList.getTypeParameterElements();
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter classParameter = typeParameters[i];
        final PsiTypeElement typeElement = referenceElements[i];
        final PsiType type = typeElement.getType();
        if (!(type instanceof PsiClassType)) continue;
        final PsiClass referenceClass = ((PsiClassType)type).resolve();
        if (referenceClass == null) continue;
        final PsiClassType[] bounds = classParameter.getSuperTypes();
        for (PsiClassType type1 : bounds) {
          PsiType bound = substitutor.substituteWithoutBoundsPromotion(type1);
          if (!TypeConversionUtil.isAssignable(bound, type, false)) {
            PsiClass boundClass = bound instanceof PsiClassType ? ((PsiClassType)bound).resolve() : null;

            @NonNls final String messageKey = boundClass == null || referenceClass.isInterface() == boundClass.isInterface()
                                              ? "generics.type.parameter.is.not.within.its.bound.extend"
                                              : "generics.type.parameter.is.not.within.its.bound.implement";

            String description = JavaErrorMessages.message(messageKey,
                                                           HighlightUtil.formatClass(referenceClass),
                                                           HighlightUtil.formatType(bound));

            final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                                  typeElement,
                                                                                  description);
            if (bound instanceof PsiClassType) {
              QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createExtendsListFix(referenceClass, (PsiClassType)bound, true), null, null);
            }
            return highlightInfo;
          }
        }
      }
    }

    return null;
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
    HighlightInfo errorResult = null;
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom == null) return null;
    if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
      final String description = HighlightClassUtil.INTERFACE_EXPECTED;
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, description);
      PsiClassType type = aClass.getManager().getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, new MoveBoundClassToFrontFix(aClass, type), null, null);
    }
    else if (referenceElements.length != 0 && element != referenceElements[0] && referenceElements[0].resolve() instanceof PsiTypeParameter) {
      final String description = JavaErrorMessages.message("type.parameter.cannot.be.followed.by.other.bounds");
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, description);
      PsiClassType type = aClass.getManager().getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      IntentionAction fix = QUICK_FIX_FACTORY.createExtendsListFix(aClass, type, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix, null, null);
    }
    return errorResult;
  }
  public static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass) {
    if (aClass instanceof PsiTypeParameter) return null;
    final PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return null;
    Map<PsiClass, PsiSubstitutor> inheritedClasses = new HashMap<PsiClass, PsiSubstitutor>();
    final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
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

  public static List<HighlightInfo> checkOverrideEquivalentMethods(final PsiClass aClass) {
    final Collection<HierarchicalMethodSignature> signaturesWithSupers = aClass.getVisibleSignatures();
    PsiManager manager = aClass.getManager();
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods =
      new THashMap<MethodSignature, MethodSignatureBackedByPsiMethod>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
    Map<MethodSignature, MethodSignatureBackedByPsiMethod> toCheckSubsignature = new HashMap<MethodSignature, MethodSignatureBackedByPsiMethod>();

    for (HierarchicalMethodSignature signature : signaturesWithSupers) {
      checkSameErasureNotSubsignatureInner(signature, manager, aClass, toCheckSubsignature, result, sameErasureMethods);
    }

    return result;
  }

  private static void checkSameErasureNotSubsignatureInner(final HierarchicalMethodSignature signature,
                                                           final PsiManager manager,
                                                           final PsiClass aClass,
                                                           final Map<MethodSignature, MethodSignatureBackedByPsiMethod> toCheckSubsignature,
                                                           final List<HighlightInfo> result,
                                                           final Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods) {
    PsiMethod method = signature.getMethod();
    if (!manager.getResolveHelper().isAccessible(method, aClass, null)) return;
    MethodSignature signatureToErase = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignatureBackedByPsiMethod sameErasure = sameErasureMethods.get(signatureToErase);
    if (sameErasure != null) {
      checkSameErasureNotSubsignatureOrSameClass(sameErasure, toCheckSubsignature, signature, aClass, method, result);
    }
    sameErasureMethods.put(signatureToErase, signature);
    toCheckSubsignature.put(signature, signature);
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : supers) {
      checkSameErasureNotSubsignatureInner(superSignature, manager, aClass, toCheckSubsignature, result, sameErasureMethods);
    }
  }

  private static void checkSameErasureNotSubsignatureOrSameClass(
    final MethodSignatureBackedByPsiMethod signatureToCheck,
    final Map<MethodSignature, MethodSignatureBackedByPsiMethod> toCheckSubsignature,
    final HierarchicalMethodSignature superSignature,
    final PsiClass aClass,
    final PsiMethod superMethod,
    final List<HighlightInfo> highlights) {

    MethodSignatureBackedByPsiMethod toCheck = toCheckSubsignature.get(signatureToCheck);
    final PsiMethod checkMethod = toCheck.getMethod();
    if (checkMethod.isConstructor()) {
      if (!superMethod.isConstructor() || !checkMethod.getContainingClass().equals(superMethod.getContainingClass())) return;
    } else if (superMethod.isConstructor()) return;

    if (checkMethod.hasModifierProperty(PsiModifier.STATIC)) {
      if (!checkMethod.getContainingClass().equals(superMethod.getContainingClass())) return;
    }

    final PsiType returnType1 = TypeConversionUtil.erasure(checkMethod.getReturnType());
    final PsiType returnType2 = TypeConversionUtil.erasure(superMethod.getReturnType());
    if (!Comparing.equal(returnType1, returnType2)) return;

    if (checkMethod.getContainingClass().equals(superMethod.getContainingClass()) ||
        !MethodSignatureUtil.isSubsignature(superSignature, toCheck)) {
      PsiMethod method1 = signatureToCheck.getMethod();
      if (aClass.equals(method1.getContainingClass())) {
        boolean sameClass = method1.getContainingClass().equals(superMethod.getContainingClass());
        highlights.add(getSameErasureMessage(sameClass, method1, superMethod));
      }
      else {
        final String descr = JavaErrorMessages.message(
          "generics.methods.have.same.erasure.override",
          HighlightMethodUtil.createClashMethodMessage(method1, superMethod, true)
        );
        TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
        highlights.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, descr));
      }
    }
  }

  private static HighlightInfo getSameErasureMessage(final boolean sameClass, final PsiMethod method, final PsiMethod superMethod) {
    @NonNls final String key = sameClass ? "generics.methods.have.same.erasure" : "generics.methods.have.same.erasure.override";
    String description = JavaErrorMessages.message(key,
                                                   HighlightMethodUtil.createClashMethodMessage(method, superMethod, !sameClass));
    TextRange textRange = HighlightUtil.getMethodDeclarationTextRange(method);
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
  }

  public static HighlightInfo checkTypeParameterInstantiation(PsiNewExpression expression) {
    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    if (classReference == null) {
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) classReference = anonymousClass.getBaseClassReference();
    }
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
        LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement);
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
    if (type instanceof PsiPrimitiveType ||
        type instanceof PsiWildcardType && ((PsiWildcardType)type).getBound() instanceof PsiPrimitiveType) {
      final PsiElement element = new PsiMatcherImpl(typeElement)
        .parent(PsiMatcherImpl.hasClass(PsiReferenceParameterList.class))
        .parent(PsiMatcherImpl.hasClass(PsiJavaCodeReferenceElement.class))
        .getElement();
      if (element == null) return null;

      String description = JavaErrorMessages.message("generics.type.argument.cannot.be.of.primitive.type");
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, description);
    }

    return null;
  }

  //precondition: TypeConversionUtil.isAssignable(lType, rType) || expressionAssignable
  public static HighlightInfo checkRawToGenericAssignment(PsiType lType, PsiType rType, @NotNull PsiElement elementToHighlight) {
    if (PsiUtil.getLanguageLevel(elementToHighlight).compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    final HighlightDisplayKey key = HighlightDisplayKey.find(UncheckedWarningLocalInspection.SHORT_NAME);
    if (!InspectionProjectProfileManager.getInstance(elementToHighlight.getProject()).getInspectionProfile(elementToHighlight).isToolEnabled(key)) return null;
    if (!isRawToGeneric(lType, rType)) return null;
    String description = JavaErrorMessages.message("generics.unchecked.assignment",
                                                   HighlightUtil.formatType(rType),
                                                   HighlightUtil.formatType(lType));

    if (InspectionManagerEx.inspectionResultSuppressed(elementToHighlight, UncheckedWarningLocalInspection.ID)) return null;
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.UNCHECKED_WARNING,
                                                                    elementToHighlight,
                                                                    description);
    List<IntentionAction> options = IntentionManager.getInstance(elementToHighlight.getProject()).getStandardIntentionOptions(key,elementToHighlight);
    String displayName = UncheckedWarningLocalInspection.DISPLAY_NAME;
    QuickFixAction.registerQuickFixAction(highlightInfo, new GenerifyFileFix(elementToHighlight.getContainingFile()), options, displayName);
    return highlightInfo;
  }

  private static boolean isRawToGeneric(PsiType lType, PsiType rType) {
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
    PsiSubstitutor lSubstitutor = lResolveResult.getSubstitutor();
    PsiSubstitutor rSubstitutor = rResolveResult.getSubstitutor();
    if (lClass == null || rClass == null) return false;
    if (lClass instanceof PsiTypeParameter &&
        !InheritanceUtil.isInheritorOrSelf(rClass, lClass, true)) return true;
    PsiClass base;
    if (!lClass.getManager().areElementsEquivalent(lClass, rClass)) {
      if (lClass.isInheritor(rClass, true)) {
        base = rClass;
        lSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(rClass, lClass, lSubstitutor);
      }
      else if (rClass.isInheritor(lClass, true)) {
        base = lClass;
        rSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(lClass, rClass, rSubstitutor);
      }
      else {
        return false;
      }
    }
    else {
      base = lClass;
    }

    LOG.assertTrue(lSubstitutor != null && rSubstitutor != null);
    Iterator<PsiTypeParameter> it = PsiUtil.typeParametersIterator(base);
    while (it.hasNext()) {
      PsiTypeParameter parameter = it.next();
      PsiType lTypeArg = lSubstitutor.substitute(parameter);
      PsiType rTypeArg = rSubstitutor.substituteWithBoundsPromotion(parameter);
      if (lTypeArg == null) continue;
      if (rTypeArg == null) {
        if (!(lTypeArg instanceof PsiWildcardType) ||
            ((PsiWildcardType) lTypeArg).getBound() != null) return true; else continue;
      }
      if (isUncheckedTypeArgumentConversion(lTypeArg, rTypeArg)) return true;
    }
    return false;
  }

  public static HighlightInfo checkUncheckedTypeCast(PsiTypeCastExpression typeCast) {
    if (PsiUtil.getLanguageLevel(typeCast).compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    final HighlightDisplayKey key = HighlightDisplayKey.find(UncheckedWarningLocalInspection.SHORT_NAME);
    if (!InspectionProjectProfileManager.getInstance(typeCast.getProject()).getInspectionProfile(typeCast).isToolEnabled(key)) return null;
    final PsiTypeElement typeElement = typeCast.getCastType();
    if (typeElement == null) return null;
    final PsiType castType = typeElement.getType();
    final PsiExpression expression = typeCast.getOperand();
    if (expression == null) return null;
    final PsiType exprType = expression.getType();
    if (exprType == null) return null;
    if (isUncheckedCast(castType, exprType)) {
      String description = JavaErrorMessages.message("generics.unchecked.cast",
                                                     HighlightUtil.formatType(exprType),
                                                     HighlightUtil.formatType(castType));
      if (InspectionManagerEx.inspectionResultSuppressed(expression, UncheckedWarningLocalInspection.ID)) return null;

      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.UNCHECKED_WARNING,
                                                                      typeCast,
                                                                      description);
      List<IntentionAction> options = IntentionManager.getInstance(expression.getProject()).getStandardIntentionOptions(key,expression);
      String displayName = UncheckedWarningLocalInspection.DISPLAY_NAME;
      QuickFixAction.registerQuickFixAction(highlightInfo, new GenerifyFileFix(expression.getContainingFile()), options, displayName);
      return highlightInfo;
    }
    return null;
  }

  private static boolean isUncheckedCast(PsiType castType, PsiType operandType) {
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
          final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(castClass);
          while(iterator.hasNext()) {
            final PsiTypeParameter typeParameter = iterator.next();
            PsiSubstitutor modifiedSubstitutor = castSubstitutor.put(typeParameter, null);
            PsiClassType otherType = typeParameter.getManager().getElementFactory().createType(castClass, modifiedSubstitutor);
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
    if (lTypeArg instanceof PsiWildcardType || rTypeArg instanceof PsiWildcardType) {
      return !lTypeArg.isAssignableFrom(rTypeArg);
    }
    if (lTypeArg instanceof PsiCapturedWildcardType || rTypeArg instanceof PsiCapturedWildcardType) return true;

    if (lTypeArg instanceof PsiArrayType && rTypeArg instanceof PsiArrayType) {
      return isUncheckedTypeArgumentConversion(((PsiArrayType)rTypeArg).getComponentType(), ((PsiArrayType)lTypeArg).getComponentType());
    }
    if (lTypeArg instanceof PsiArrayType || rTypeArg instanceof PsiArrayType) return false;
    LOG.assertTrue(lTypeArg instanceof PsiClassType && rTypeArg instanceof PsiClassType);
    return ((PsiClassType)lTypeArg).resolve() instanceof PsiTypeParameter ||
           ((PsiClassType)rTypeArg).resolve() instanceof PsiTypeParameter;
  }

  public static HighlightInfo checkUncheckedCall(JavaResolveResult resolveResult, PsiCall call) {
    if (PsiUtil.getLanguageLevel(call).compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    final HighlightDisplayKey key = HighlightDisplayKey.find(UncheckedWarningLocalInspection.SHORT_NAME);
    if (!InspectionProjectProfileManager.getInstance(call.getProject()).getInspectionProfile(call).isToolEnabled(key)) return null;

    final PsiMethod method = (PsiMethod)resolveResult.getElement();
    if (method == null) return null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (final PsiParameter parameter : parameters) {
      final PsiType parameterType = parameter.getType();
      if (parameterType.accept(new PsiTypeVisitor<Boolean>() {
        public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
          return Boolean.FALSE;
        }

        public Boolean visitArrayType(PsiArrayType arrayType) {
          return arrayType.getComponentType().accept(this);
        }

        public Boolean visitClassType(PsiClassType classType) {
          PsiClass psiClass = classType.resolve();
          if (psiClass instanceof PsiTypeParameter) {
            return substitutor.substitute((PsiTypeParameter)psiClass) == null ? Boolean.TRUE : Boolean.FALSE;
          }
          PsiType[] parameters = classType.getParameters();
          for (PsiType parameter : parameters) {
            if (parameter.accept(this).booleanValue()) return Boolean.TRUE;

          }
          return Boolean.FALSE;
        }

        public Boolean visitWildcardType(PsiWildcardType wildcardType) {
          PsiType bound = wildcardType.getBound();
          if (bound != null) return bound.accept(this);
          return Boolean.FALSE;
        }

        public Boolean visitEllipsisType(PsiEllipsisType ellipsisType) {
          return ellipsisType.getComponentType().accept(this);
        }
      }).booleanValue()) {
        final PsiElementFactory elementFactory = method.getManager().getElementFactory();
        PsiType type = elementFactory.createType(method.getContainingClass(), substitutor);
        String description = JavaErrorMessages.message("generics.unchecked.call.to.member.of.raw.type",
                                                       HighlightUtil.formatMethod(method),
                                                       HighlightUtil.formatType(type));
        PsiElement element = call instanceof PsiMethodCallExpression
                             ? ((PsiMethodCallExpression)call).getMethodExpression()
                             : call;
        if (InspectionManagerEx.inspectionResultSuppressed(call, UncheckedWarningLocalInspection.ID)) return null;
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.UNCHECKED_WARNING, element, description);
        List<IntentionAction> options = IntentionManager.getInstance(call.getProject()).getStandardIntentionOptions(key,call);
        String displayName = UncheckedWarningLocalInspection.DISPLAY_NAME;
        QuickFixAction.registerQuickFixAction(highlightInfo, new GenerifyFileFix(element.getContainingFile()), options, displayName);
        return highlightInfo;
      }
    }
    return null;
  }

  public static HighlightInfo checkForeachLoopParameterType(PsiForeachStatement statement) {
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression expression = statement.getIteratedValue();
    if (expression == null) return null;
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
      String displayName = UncheckedWarningLocalInspection.DISPLAY_NAME;
      QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(parameter, itemType), null, displayName);
    } else {
      highlightInfo = checkRawToGenericAssignment(parameterType, itemType, statement.getIterationParameter());
    }
    return highlightInfo;
  }

  private static PsiType getCollectionItemType(PsiExpression expression) {
    final PsiType type = expression.getType();
    if (type == null) return null;
    if (type instanceof PsiArrayType) {
      return ((PsiArrayType)type).getComponentType();
    }
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return null;
      final PsiManager manager = aClass.getManager();
      final PsiClass iterable = manager.findClass("java.lang.Iterable", aClass.getResolveScope());
      if (iterable == null) return null;
      final PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(iterable, aClass, PsiSubstitutor.EMPTY);
      if (substitutor == null) return null;
      PsiTypeParameter[] typeParameters = iterable.getTypeParameters();
      if (typeParameters.length != 1) return null;
      final PsiTypeParameter itemTypeParameter = typeParameters[0];
      PsiType itemType = substitutor.substitute(itemTypeParameter);
      itemType = resolveResult.getSubstitutor().substitute(itemType);
      return itemType == null ? PsiType.getJavaLangObject(manager, aClass.getResolveScope()) : itemType;
    }
    return null;
  }

  public static HighlightInfo checkAccessStaticFieldFromEnumConstructor(PsiReferenceExpression expr, JavaResolveResult result) {
    final PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiField)) return null;
    if (!((PsiField)resolved).hasModifierProperty(PsiModifier.STATIC)) return null;
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
    if (PsiUtil.getLanguageLevel(parameterList).compareTo(LanguageLevel.JDK_1_5) < 0) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               parameterList,
                                               GENERICS_ARE_NOT_SUPPORTED);
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
      ref = ((PsiJavaCodeReferenceElement)ref).getQualifier();
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
  public static HighlightInfo checkOverrideAnnotation(PsiMethod method) {
    PsiModifierList list = method.getModifierList();
    final PsiAnnotation overrideAnnotation = list.findAnnotation("java.lang.Override");
    if (overrideAnnotation != null) {
      for (PsiMethod superMethod : method.findSuperMethods()) {
        if (!superMethod.getContainingClass().isInterface()) return null;
      }
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, overrideAnnotation,
                                               JavaErrorMessages.message("override.annotation.violated"));
    }
    return null;
  }

  static HighlightInfo checkEnumConstantForConstructorProblems(PsiEnumConstant enumConstant) {
    PsiClass containingClass = enumConstant.getContainingClass();
    if (enumConstant.getInitializingClass() == null) {
      HighlightInfo highlightInfo = HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, enumConstant.getNameIdentifier());
      if (highlightInfo != null) return highlightInfo;
      highlightInfo = HighlightClassUtil.checkClassWithAbstractMethods(enumConstant.getContainingClass(), enumConstant.getNameIdentifier());
      if (highlightInfo != null) return highlightInfo;
    }
    PsiClassType type = enumConstant.getManager().getElementFactory().createType(containingClass);

    return HighlightMethodUtil.checkConstructorCall(type.resolveGenerics(), enumConstant, type, null);
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
          return HighlightInfo
            .createHighlightInfo(HighlightInfoType.ERROR, parameter, JavaErrorMessages.message("varargs.prior.15"));
        }

        if (params[params.length - 1] != parameter) {
          HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                 parameter,
                                                                 JavaErrorMessages.message("vararg.not.last.parameter"));
          String displayName = UncheckedWarningLocalInspection.DISPLAY_NAME;
          QuickFixAction.registerQuickFixAction(info, new MakeVarargParameterLastFix(parameter), null, displayName);
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

  public static HighlightInfo checkGenericCallWithRawArguments(JavaResolveResult resolveResult, PsiCallExpression callExpression) {
    final PsiMethod method = (PsiMethod)resolveResult.getElement();
    if (method == null) return null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList == null) return null;
    final PsiExpression[] expressions = argumentList.getExpressions();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < expressions.length; i++) {
      PsiParameter parameter = parameters[Math.min(i, parameters.length - 1)];
      final PsiExpression expression = expressions[i];
      final PsiType parameterType = substitutor.substitute(parameter.getType());
      final PsiType expressionType = substitutor.substitute(expression.getType());
      if (expressionType != null) {
        final HighlightInfo highlightInfo = checkRawToGenericAssignment(parameterType, expressionType, expression);
        if (highlightInfo != null) return highlightInfo;
      }
    }
    return null;
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
      if (((PsiTypeParameterListOwner)element).hasModifierProperty(PsiModifier.STATIC)) return null;
      PsiClass containingClass = ((PsiTypeParameterListOwner)element).getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, resolveResult.getSubstitutor())) {
        final String message;
        if (element instanceof PsiClass) {
          message = JavaErrorMessages.message("generics.type.arguments.on.raw.type");
        }
        else {
          message = JavaErrorMessages.message("generics.type.arguments.on.raw.method");
        }

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
        throwableClass = aClass.getManager().findClass("java.lang.Throwable", aClass.getResolveScope());
      }
      if (InheritanceUtil.isInheritorOrSelf((PsiClass)resolved, throwableClass, true)) {
        String message = JavaErrorMessages.message("generic.extend.exception");
        HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, referenceElement, message);
        PsiClassType classType = aClass.getManager().getElementFactory().createType((PsiClass)resolved);
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createExtendsListFix(aClass, classType, false));
        return highlightInfo;
      }
    }
    return null;
  }

  public static HighlightInfo checkUncheckedOverriding (PsiMethod overrider, final List<MethodSignatureBackedByPsiMethod> superMethodSignatures) {
    if (PsiUtil.getLanguageLevel(overrider).compareTo(LanguageLevel.JDK_1_5) < 0) return null;
    final HighlightDisplayKey key = HighlightDisplayKey.find(UncheckedWarningLocalInspection.SHORT_NAME);
    if (!InspectionProjectProfileManager.getInstance(overrider.getProject()).getInspectionProfile(overrider).isToolEnabled(key)) return null;
    if (InspectionManagerEx.inspectionResultSuppressed(overrider, UncheckedWarningLocalInspection.ID)) return null;
    final MethodSignature signature = overrider.getSignature(PsiSubstitutor.EMPTY);
    for (MethodSignatureBackedByPsiMethod superSignature : superMethodSignatures) {
      PsiMethod baseMethod = superSignature.getMethod();
      PsiSubstitutor substitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(signature, superSignature);
      if (substitutor == null) substitutor = superSignature.getSubstitutor();
      if (PsiUtil.isRawSubstitutor(baseMethod, superSignature.getSubstitutor())) continue;
      final PsiType baseReturnType = substitutor.substitute(baseMethod.getReturnType());
      final PsiType overriderReturnType = overrider.getReturnType();
      if (baseReturnType == null || overriderReturnType == null) return null;
      if (isRawToGeneric(baseReturnType, overriderReturnType)) {
        final String message = JavaErrorMessages.message("unchecked.overriding.incompatible.return.type",
                                                         HighlightUtil.formatType(overriderReturnType),
                                                         HighlightUtil.formatType(baseReturnType));

        final PsiTypeElement returnTypeElement = overrider.getReturnTypeElement();
        LOG.assertTrue(returnTypeElement != null);
        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.UNCHECKED_WARNING, returnTypeElement, message);
        List<IntentionAction> options = IntentionManager.getInstance(overrider.getProject()).getStandardIntentionOptions(key,returnTypeElement);
        String displayName = UncheckedWarningLocalInspection.DISPLAY_NAME;
        QuickFixAction.registerQuickFixAction(highlightInfo,
                                              new EmptyIntentionAction(JavaErrorMessages.message("unchecked.overriding"), options),
                                              options,
                                              displayName);

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
                                               ClassUtil.getClassDeclarationTextRange(aClass),
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
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 parameterList, message);
        }
      }
    }
    return null;
  }
}


// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

public class JavaGenericsUtil {
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

      if (parameters.length > 0) {
        for (PsiType parameter : parameters) {
          if (!(parameter instanceof PsiWildcardType && ((PsiWildcardType)parameter).getBound() == null)) {
            return false;
          }
        }
        return true;
      }

      assert parameters.length == 0;
      final PsiClassType.ClassResolveResult resolved = classType.resolveGenerics();
      final PsiClass aClass = resolved.getElement();
      if (aClass instanceof PsiTypeParameter) {
        return false;
      }

      if (aClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
        //local class (inner inside inside anonymous) should skip anonymous as it can't be static itself
        final PsiClass stopClassLevel = PsiUtil.isLocalClass(aClass) ? null : aClass.getContainingClass();
        PsiModifierListOwner enclosingStaticElement = PsiUtil.getEnclosingStaticElement(aClass, stopClassLevel);
        PsiClass containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
        if (containingClass != null && (enclosingStaticElement == null || PsiTreeUtil.isAncestor(enclosingStaticElement, containingClass, false))) {
          //anonymous classes are not generic
          while (containingClass instanceof PsiAnonymousClass) {
            containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, true);
          }
          if (containingClass == null || enclosingStaticElement != null && !PsiTreeUtil.isAncestor(enclosingStaticElement, containingClass, false)) {
            return true;
          }
          return isReifiableType(JavaPsiFacade.getElementFactory(aClass.getProject()).createType(containingClass, resolved.getSubstitutor()));
        }
      }
      return true;
    }

    if (type instanceof PsiCapturedWildcardType) {
      return isReifiableType(((PsiCapturedWildcardType)type).getUpperBound());
    }

    return false;
  }

  public static boolean isUncheckedWarning(@NotNull PsiJavaCodeReferenceElement expression,
                                           @NotNull JavaResolveResult resolveResult,
                                           @NotNull LanguageLevel languageLevel) {
    final PsiElement resolve = resolveResult.getElement();
    if (!(resolve instanceof PsiMethod)) {
      return false;
    }
    PsiMethod psiMethod = (PsiMethod)resolve;

    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();

    int parametersCount = parameters.length;
    if (parametersCount == 0) {
      return false;
    }
    PsiParameter varargParameter = parameters[parametersCount - 1];
    if (!varargParameter.isVarArgs()) {
      return false;
    }

    if (AnnotationUtil.isAnnotated(psiMethod, CommonClassNames.JAVA_LANG_SAFE_VARARGS, CHECK_EXTERNAL)) {
      return false;
    }

    PsiType componentType = ((PsiEllipsisType)varargParameter.getType()).getComponentType();
    if (isReifiableType(resolveResult.getSubstitutor().substitute(componentType))) {
      return false;
    }

    if (expression instanceof PsiMethodReferenceExpression) return true;

    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiCall) {
      final PsiExpressionList argumentList = ((PsiCall)parent).getArgumentList();
      if (argumentList != null) {
        final PsiExpression[] args = argumentList.getExpressions();
        if (args.length == parametersCount) {
          final PsiExpression lastArg = args[args.length - 1];
          if (lastArg.getType() instanceof PsiArrayType) {
            return false;
          }
        }
        for (int i = parametersCount - 1; i < args.length; i++) {
          if (!isReifiableType(resolveResult.getSubstitutor().substitute(args[i].getType()))) {
            return true;
          }
        }
        return args.length < parametersCount;
      }
    }
    return false;
  }

  public static boolean isUncheckedCast(PsiType castType, PsiType operandType) {
    if (TypeConversionUtil.isAssignable(castType, operandType, false)) return false;

    castType = castType.getDeepComponentType();
    if (castType instanceof PsiClassType) {
      final PsiClassType castClassType = (PsiClassType)castType;
      operandType = operandType.getDeepComponentType();
      if (operandType instanceof PsiCapturedWildcardType) {
        operandType = ((PsiCapturedWildcardType)operandType).getUpperBound();
      }

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
          PsiElementFactory factory = JavaPsiFacade.getInstance(castClass.getProject()).getElementFactory();
          for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(castClass)) {
            PsiSubstitutor modifiedSubstitutor = castSubstitutor.put(typeParameter, null);
            PsiClassType otherType = factory.createType(castClass, modifiedSubstitutor);
            if (TypeConversionUtil.isAssignable(operandType, otherType, false)) return true;
          }
          for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(operandClass)) {
            final PsiType operand = operandResult.getSubstitutor().substitute(typeParameter);
            if (operand instanceof PsiCapturedWildcardType) return true;
          }
          return false;
        }
        return true;
      }
    }

    return false;
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
    }
    if (lType instanceof PsiIntersectionType) {
      for (PsiType type : ((PsiIntersectionType)lType).getConjuncts()) {
        if (isRawToGeneric(type, rType)) return true;
      }
      return false;
    }

    if (rType instanceof PsiCapturedWildcardType) {
      return isRawToGeneric(lType, ((PsiCapturedWildcardType)rType).getUpperBound());
    }

    if (!(lType instanceof PsiClassType) || !(rType instanceof PsiClassType)) return false;

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
        !InheritanceUtil.isInheritorOrSelf(rClass, lClass, true)) {
      return true;
    }

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
        if (lTypeArg instanceof PsiWildcardType && ((PsiWildcardType)lTypeArg).getBound() == null) {
          continue;
        }
        else {
          return true;
        }
      }
      if (!TypeConversionUtil.typesAgree(lTypeArg, rTypeArg, true)) return true;
    }
    return false;
  }

  @Nullable
  public static PsiType getCollectionItemType(@NotNull PsiExpression expression) {
    return getCollectionItemType(expression.getType(), expression.getResolveScope());
  }

  @Nullable
  public static PsiType getCollectionItemType(@Nullable PsiType type, @NotNull GlobalSearchScope scope) {
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
        PsiClass myClass = facade.findClass(qName, scope);
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
      PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getClassSubstitutor(owner, aClass, substitutor);
      if (superClassSubstitutor == null) return null;
      PsiType itemType = superClassSubstitutor.substitute(typeParameter);
      return itemType == null ? PsiType.getJavaLangObject(manager, aClass.getResolveScope()) : itemType;
    }
    if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        final PsiType itemType = getCollectionItemType(conjunct, scope);
        if (itemType != null) {
          return itemType;
        }
      }
    }
    if (type instanceof PsiCapturedWildcardType) {
      return getCollectionItemType(((PsiCapturedWildcardType)type).getUpperBound(), scope);
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
}

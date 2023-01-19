// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class JavaClassSupersImpl extends JavaClassSupers {
  private static final Logger LOG = Logger.getInstance(JavaClassSupersImpl.class);

  @Override
  @Nullable
  public PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                 @NotNull PsiClass derivedClass,
                                                 @NotNull GlobalSearchScope scope,
                                                 @NotNull PsiSubstitutor derivedSubstitutor) {
    if (InheritanceImplUtil.hasObjectQualifiedName(superClass)) return PsiSubstitutor.EMPTY;
    List<PsiType> bounds = null;
    if (superClass instanceof InferenceVariable) {
      bounds = ((InferenceVariable)superClass).getBounds(InferenceBound.LOWER);
    }
    else if (superClass instanceof PsiTypeParameter) {
      final PsiType lowerBound = TypeConversionUtil.getInferredLowerBoundForSynthetic((PsiTypeParameter)superClass);
      if (lowerBound != null) {
        bounds = Collections.singletonList(lowerBound);
      }
    }
    if (bounds != null) {
      for (PsiType lowerBound : bounds) {
        if (lowerBound != null) {
          final PsiSubstitutor substitutor = processLowerBound(lowerBound, derivedClass, scope, derivedSubstitutor);
          if (substitutor != null) {
            return substitutor;
          }
        }
      }
    }

    return derivedClass instanceof PsiTypeParameter
           ? processTypeParameter((PsiTypeParameter)derivedClass, scope, superClass, new HashSet<>(), derivedSubstitutor)
           : getSuperSubstitutorWithCaching(superClass, derivedClass, scope, derivedSubstitutor);
  }

  private static PsiSubstitutor processLowerBound(@NotNull PsiType lowerBound,
                                                  @NotNull PsiClass derivedClass,
                                                  @NotNull GlobalSearchScope scope,
                                                  @NotNull PsiSubstitutor derivedSubstitutor) {
    if (lowerBound instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = ((PsiClassType)lowerBound).resolveGenerics();
      final PsiClass boundClass = result.getElement();
      if (boundClass != null) {
        if (boundClass.equals(derivedClass)) {
          return derivedSubstitutor;
        }
        final PsiSubstitutor substitutor = getSuperSubstitutorWithCaching(boundClass,
                                                                          derivedClass, scope, result.getSubstitutor());
        if (substitutor != null) {
          return composeSubstitutors(derivedSubstitutor, substitutor, boundClass);
        }
      }
    }
    else if (lowerBound instanceof PsiIntersectionType) {
      for (PsiType bound : ((PsiIntersectionType)lowerBound).getConjuncts()) {
        final PsiSubstitutor substitutor = processLowerBound(bound, derivedClass, scope, derivedSubstitutor);
        if (substitutor != null) {
          return substitutor;
        }
      }
    }
    else if (lowerBound instanceof PsiCapturedWildcardType) {
      return processLowerBound(((PsiCapturedWildcardType)lowerBound).getUpperBound(), derivedClass, scope, derivedSubstitutor);
    }
    return null;
  }

  @Nullable
  private static PsiSubstitutor getSuperSubstitutorWithCaching(@NotNull PsiClass superClass,
                                                               @NotNull PsiClass derivedClass,
                                                               @NotNull GlobalSearchScope resolveScope,
                                                               @NotNull PsiSubstitutor derivedSubstitutor) {
    PsiSubstitutor substitutor = ScopedClassHierarchy.getSuperClassSubstitutor(derivedClass, resolveScope, superClass);
    if (substitutor == null) return null;
    if (PsiUtil.isRawSubstitutor(derivedClass, derivedSubstitutor)) return createRawSubstitutor(superClass);

    return composeSubstitutors(derivedSubstitutor, substitutor, superClass);
  }

  @NotNull
  static PsiSubstitutor createRawSubstitutor(@NotNull PsiClass superClass) {
    return JavaPsiFacade.getElementFactory(superClass.getProject()).createRawSubstitutor(superClass);
  }

  @NotNull
  private static PsiSubstitutor composeSubstitutors(PsiSubstitutor outer, PsiSubstitutor inner, PsiClass onClass) {
    PsiSubstitutor answer = PsiSubstitutor.EMPTY;
    Map<PsiTypeParameter, PsiType> outerMap = outer.getSubstitutionMap();
    Map<PsiTypeParameter, PsiType> innerMap = inner.getSubstitutionMap();
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(onClass)) {
      if (outerMap.containsKey(parameter) || innerMap.containsKey(parameter)) {
        PsiType innerType = inner.substitute(parameter);
        PsiClass paramCandidate = PsiCapturedWildcardType.isCapture() ? PsiUtil.resolveClassInClassTypeOnly(innerType) : null;
        PsiType targetType;
        if (paramCandidate instanceof PsiTypeParameter && paramCandidate != parameter) {
          targetType = outer.substituteWithBoundsPromotion((PsiTypeParameter)paramCandidate);
          if (targetType != null && innerType.getAnnotations().length > 0) {
            PsiAnnotation[] typeAnnotations = targetType.getAnnotations();
            targetType = targetType.annotate(() -> ArrayUtil.mergeArrays(innerType.getAnnotations(), typeAnnotations));
          }
        }
        else {
          targetType = outer.substitute(innerType);
        }
        answer = answer.put(parameter, targetType);
      }
    }
    return answer;
  }

  /**
   * Some type parameters (e.g. {@link InferenceVariable} change their supers at will,
   * so caching the hierarchy is impossible.
   */
  @Nullable
  private static PsiSubstitutor processTypeParameter(PsiTypeParameter parameter,
                                                     GlobalSearchScope scope,
                                                     PsiClass superClass,
                                                     Set<? super PsiTypeParameter> visited,
                                                     PsiSubstitutor derivedSubstitutor) {
    if (parameter.getManager().areElementsEquivalent(parameter, superClass)) return PsiSubstitutor.EMPTY;
    if (!visited.add(parameter)) return null;

    for (PsiClassType type : parameter.getExtendsListTypes()) {
      PsiClassType.ClassResolveResult result = type.resolveGenerics();
      PsiClass psiClass = result.getElement();
      if (psiClass == null) continue;

      PsiSubstitutor answer;
      if (psiClass instanceof PsiTypeParameter) {
        answer = processTypeParameter((PsiTypeParameter)psiClass, scope, superClass, visited, derivedSubstitutor);
        if (answer != null) {
          return answer;
        }
      }
      else {
        answer = getSuperSubstitutorWithCaching(superClass, psiClass, scope, result.getSubstitutor());
        if (answer != null) {
          return composeSubstitutors(derivedSubstitutor, answer, superClass);
        }
      }
    }

    return null;
  }

  private static final Set<String> ourReportedInconsistencies = ContainerUtil.newConcurrentSet();

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  @Override
  public void reportHierarchyInconsistency(@NotNull PsiClass superClass, @NotNull PsiClass derivedClass) {
    if (!ourReportedInconsistencies.add(derivedClass.getQualifiedName() + "/" + superClass.getQualifiedName()) &&
        !ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    StringBuilder msg = new StringBuilder("superClassSubstitutor requested when derived doesn't extend super:\n");
    msg.append("Super: " + classInfo(superClass));
    msg.append("Derived: " + classInfo(derivedClass));
    msg.append("isInheritor: via util=" +
               InheritanceUtil.isInheritorOrSelf(derivedClass, superClass, true) +
               ", directly=" +
               derivedClass.isInheritor(superClass, true) + "\n");
    msg.append("Super in derived's scope: " + PsiSearchScopeUtil.isInScope(derivedClass.getResolveScope(), superClass) + "\n");
    if (!InheritanceUtil.processSupers(derivedClass, false, s -> s != superClass)) {
      msg.append("Plain derived's supers contain Super\n");
    }
    msg.append("Hierarchy:\n");
    new ScopedClassHierarchy(derivedClass, derivedClass.getResolveScope()) {
      @Override
      void visitType(@NotNull PsiClassType type, Map<PsiClass, PsiClassType.ClassResolveResult> map) {
        PsiClass eachClass = type.resolve();
        msg.append("  each: ");
        msg.append(eachClass == null ? "unresolved " + type : classInfo(eachClass));
        super.visitType(type, map);
      }
    }.visitType(JavaPsiFacade.getElementFactory(derivedClass.getProject()).createType(derivedClass, PsiSubstitutor.EMPTY), new HashMap<>());
    LOG.error(msg);
  }

  @SuppressWarnings("StringConcatenationInLoop")
  @NotNull
  private static String classInfo(@NotNull PsiClass aClass) {
    String s = aClass.getQualifiedName() + "(" + aClass.getClass().getName() + "; " + PsiUtilCore.getVirtualFile(aClass) + ");\n";
    s += "    extends: ";
    for (PsiClassType type : aClass.getExtendsListTypes()) {
      s += "    " + type + " (" + type.getClass().getName() + "; " + type.resolve() + ") ";
    }
    s += "\n    implements: ";
    for (PsiClassType type : aClass.getImplementsListTypes()) {
      s += "    " + type + " (" + type.getClass().getName() + "; " + type.resolve() + ") ";
    }
    return s + "\n";
  }


}

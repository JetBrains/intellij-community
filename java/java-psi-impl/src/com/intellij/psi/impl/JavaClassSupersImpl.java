/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.JavaClassSupers;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class JavaClassSupersImpl extends JavaClassSupers {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.JavaClassSupersImpl");

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
      final PsiType lowerBound = InferenceSession.getLowerBound(superClass);
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
           ? processTypeParameter((PsiTypeParameter)derivedClass, scope, superClass, ContainerUtil.newTroveSet(), derivedSubstitutor)
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
        PsiType targetType = paramCandidate instanceof PsiTypeParameter && paramCandidate != parameter
                             ? outer.substituteWithBoundsPromotion((PsiTypeParameter)paramCandidate)
                             : outer.substitute(innerType);
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
                                                     Set<PsiTypeParameter> visited, 
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
    msg.append("isInheritor: " +
               InheritanceUtil.isInheritorOrSelf(derivedClass, superClass, true) +
               " " +
               derivedClass.isInheritor(superClass, true) + "\n");
    msg.append("Super in derived's scope: " + PsiSearchScopeUtil.isInScope(derivedClass.getResolveScope(), superClass) + "\n");
    if (!InheritanceUtil.processSupers(derivedClass, false, s -> s != superClass)) {
      msg.append("Plain derived's supers contain Super:\n");
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
    };
    LOG.error(msg);
  }

  @SuppressWarnings("StringConcatenationInLoop")
  @NotNull
  private static String classInfo(@NotNull PsiClass aClass) {
    String s = aClass.getQualifiedName() + "(" + aClass.getClass().getName() + "; " + PsiUtilCore.getVirtualFile(aClass) + ");\n";
    s += "extends: ";
    for (PsiClassType type : aClass.getExtendsListTypes()) {
      s += type + " (" + type.getClass().getName() + "; " + type.resolve() + ") ";
    }
    s += "\nimplements: ";
    for (PsiClassType type : aClass.getImplementsListTypes()) {
      s += type + " (" + type.getClass().getName() + "; " + type.resolve() + ") ";
    }
    return s + "\n";
  }


}

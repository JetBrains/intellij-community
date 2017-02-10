/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.findAll;
import static com.intellij.util.containers.ContainerUtil.reverse;

/**
 * @author Dmitry Batkovich
 */
public class MethodsChain {
  private final List<PsiMethod[]> myRevertedPath;
  private final int myWeight;
  //
  // chain qualifier class could be different with method.getContainingClass()
  private final String myQualifierClassName;

  private final Set<String> myExcludedQNames;

  public MethodsChain(final PsiMethod[] methods, final int weight, final String qualifierClassName) {
    this(Collections.singletonList(methods), weight, qualifierClassName, chooseParametersQNames(methods));
  }

  private MethodsChain(final List<PsiMethod[]> revertedPath,
                       final int weight,
                       final String qualifierClassName,
                       final Set<String> excludedQNames) {
    myRevertedPath = revertedPath;
    myWeight = weight;
    myQualifierClassName = qualifierClassName;
    myExcludedQNames = excludedQNames;
  }

  public int size() {
    return myRevertedPath.size();
  }

  public Set<String> getExcludedQNames() {
    return myExcludedQNames;
  }

  public String getQualifierClassName() {
    return myQualifierClassName;
  }

  public Iterator<PsiMethod[]> iterator() {
    return myRevertedPath.iterator();
  }

  @NotNull
  public PsiMethod[] getFirst() {
    return myRevertedPath.get(0);
  }

  public List<PsiMethod[]> getPath() {
    return reverse(myRevertedPath);
  }

  public int getChainWeight() {
    return myWeight;
  }

  @SuppressWarnings("unchecked")
  public MethodsChain addEdge(final PsiMethod[] psiMethods, final String newQualifierClassName, final int newWeight) {
    final List<PsiMethod[]> newRevertedPath = new ArrayList<>(myRevertedPath.size() + 1);
    newRevertedPath.addAll(myRevertedPath);
    newRevertedPath.add(psiMethods);
    return new MethodsChain(newRevertedPath,
                            newWeight,
                            newQualifierClassName,
                            joinSets(myExcludedQNames, chooseParametersQNames(psiMethods)));
  }


  @Override
  public String toString() {
    return StringUtil.join(myRevertedPath, "<-");
  }

  @SuppressWarnings("ConstantConditions")
  public static CompareResult compare(final MethodsChain left, final MethodsChain right, final PsiManager psiManager) {
    if (left.size() == 0) {
      return CompareResult.RIGHT_CONTAINS_LEFT;
    }
    if (right.size() == 0) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    final Iterator<PsiMethod[]> leftIterator = left.myRevertedPath.iterator();
    final Iterator<PsiMethod[]> rightIterator = right.myRevertedPath.iterator();

    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      final PsiMethod thisNext = leftIterator.next()[0];
      final PsiMethod thatNext = rightIterator.next()[0];
      if (thisNext == null || thatNext == null) {
        throw new NullPointerException();
      }
      if (((thisNext.isConstructor() != thatNext.isConstructor())) || !thisNext.getName().equals(thatNext.getName())) {
        return CompareResult.NOT_EQUAL;
      }
    }
    if (leftIterator.hasNext() && !rightIterator.hasNext()) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    if (!leftIterator.hasNext() && rightIterator.hasNext()) {
      return CompareResult.RIGHT_CONTAINS_LEFT;
    }


    return hasBaseMethod(left.getPath().get(0), right.getPath().get(0), psiManager)
           ? CompareResult.EQUAL
           : CompareResult.NOT_EQUAL;
  }

  public enum CompareResult {
    LEFT_CONTAINS_RIGHT,
    RIGHT_CONTAINS_LEFT,
    EQUAL,
    NOT_EQUAL
  }

  private static boolean hasBaseMethod(final PsiMethod[] left, final PsiMethod[] right, final PsiManager psiManager) {
    for (final PsiMethod rightMethod : right) {
      final PsiMethod[] rightSupers = rightMethod.findDeepestSuperMethods();
      if (rightSupers.length != 0) {
        for (final PsiMethod leftMethod : left) {
          final PsiMethod[] leftSupers = leftMethod.findDeepestSuperMethods();
          if (leftSupers.length != 0) {
            if (psiManager.areElementsEquivalent(leftSupers[0], rightSupers[0])) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static Set<String> joinSets(final Set<String>... sets) {
    final Set<String> result = new HashSet<>();
    for (final Set<String> set : sets) {
      for (final String s : set) {
        result.add(s);
      }
    }
    return result;
  }

  private static Set<String> chooseParametersQNames(final PsiMethod[] methods) {
    final Set<String> qNames = new HashSet<>();
    for (final PsiParameter methodParameter : methods[0].getParameterList().getParameters()) {
      qNames.add(methodParameter.getType().getCanonicalText());
    }
    return qNames;
  }

}

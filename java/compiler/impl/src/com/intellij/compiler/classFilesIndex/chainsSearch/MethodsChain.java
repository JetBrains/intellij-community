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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.reverse;

public class MethodsChain {
  private final List<PsiMethod[]> myRevertedPath;
  private final int myWeight;
  private final PsiClass myQualifierClass;

  public MethodsChain(PsiClass qualifierClass, PsiMethod[] methods, int weight) {
    this(qualifierClass, weight, Collections.singletonList(methods));
  }

  private MethodsChain(PsiClass qualifierClass, int weight, List<PsiMethod[]> revertedPath) {
    myRevertedPath = revertedPath;
    myWeight = weight;
    myQualifierClass = qualifierClass;
  }

  public int size() {
    return myRevertedPath.size();
  }

  public PsiClass getQualifierClass() {
    return myQualifierClass;
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
  public MethodsChain addEdge(PsiMethod[] psiMethods, PsiClass newQualifierClassName, int newWeight) {
    List<PsiMethod[]> newRevertedPath = new ArrayList<>(myRevertedPath.size() + 1);
    newRevertedPath.addAll(myRevertedPath);
    newRevertedPath.add(psiMethods);
    return new MethodsChain(newQualifierClassName, newWeight, newRevertedPath);
  }


  @Override
  public String toString() {
    return StringUtil.join(myRevertedPath, "<-");
  }

  @SuppressWarnings("ConstantConditions")
  public static CompareResult compare(MethodsChain left, MethodsChain right, PsiManager psiManager) {
    if (left.size() == 0) {
      return CompareResult.RIGHT_CONTAINS_LEFT;
    }
    if (right.size() == 0) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    Iterator<PsiMethod[]> leftIterator = left.myRevertedPath.iterator();
    Iterator<PsiMethod[]> rightIterator = right.myRevertedPath.iterator();

    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      PsiMethod thisNext = leftIterator.next()[0];
      PsiMethod thatNext = rightIterator.next()[0];
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

  private static boolean hasBaseMethod(PsiMethod[] left, PsiMethod[] right, PsiManager psiManager) {
    for (PsiMethod rightMethod : right) {
      PsiMethod[] rightSupers = rightMethod.findDeepestSuperMethods();
      if (rightSupers.length != 0) {
        for (PsiMethod leftMethod : left) {
          PsiMethod[] leftSupers = leftMethod.findDeepestSuperMethods();
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

  private static Set<String> joinSets(Set<String>... sets) {
    Set<String> result = new HashSet<>();
    for (Set<String> set : sets) {
      for (String s : set) {
        result.add(s);
      }
    }
    return result;
  }
}

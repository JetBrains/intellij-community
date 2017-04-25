/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch;

import com.intellij.compiler.backwardRefs.MethodIncompleteSignature;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtil.reverse;

public class MethodsChain {
  private final List<PsiMethod[]> myRevertedPath;
  private final MethodIncompleteSignature mySignature;
  private final int myWeight;
  private final PsiClass myQualifierClass;

  @Nullable
  public static MethodsChain create(@NotNull MethodIncompleteSignature signature,
                             int weight,
                             @NotNull ChainCompletionContext context) {
    PsiClass qualifier = context.resolveQualifierClass(signature);
    if (qualifier == null || (!signature.isStatic() && InheritanceUtil.isInheritorOrSelf(context.getTarget().getTargetClass(), qualifier, true))) {
      return null;
    }
    PsiMethod[] methods = context.resolve(signature);
    if (methods.length == 0) return null;
    Set<PsiClass> classes = Arrays.stream(methods)
      .flatMap(m -> Arrays.stream(m.getParameterList().getParameters()))
      .map(p -> PsiUtil.resolveClassInType(p.getType()))
      .collect(Collectors.toSet());
    PsiClass contextClass = context.getTarget().getTargetClass();
    if (classes.contains(contextClass)) {
      return null;
    }
    classes.add(contextClass);
    return new MethodsChain(qualifier, Collections.singletonList(methods), signature, weight);
  }

  public MethodsChain(@NotNull PsiClass qualifierClass,
                      @NotNull List<PsiMethod[]> revertedPath,
                      MethodIncompleteSignature signature,
                      int weight) {
    myQualifierClass = qualifierClass;
    myRevertedPath = revertedPath;
    mySignature = signature;
    myWeight = weight;
  }

  @NotNull
  public MethodIncompleteSignature getHeadSignature() {
    return mySignature;
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


  public MethodsChain continuation(@NotNull MethodIncompleteSignature signature,
                                   int weight,
                                   @NotNull ChainCompletionContext context) {
    MethodsChain head = create(signature, weight, context);
    if (head == null) return null;

    ArrayList<PsiMethod[]> newRevertedPath = newArrayList();
    newRevertedPath.addAll(myRevertedPath);
    newRevertedPath.add(head.getPath().get(0));
    return new MethodsChain(head.getQualifierClass(), newRevertedPath, head.getHeadSignature(), weight);
  }

  @Override
  public String toString() {
    return myQualifierClass.getName() + "." + reverse(myRevertedPath).stream().map(methods -> methods[0].getName() + "()").collect(Collectors.joining("."));
  }

  @SuppressWarnings("ConstantConditions")
  public static CompareResult compare(@NotNull MethodsChain left, @NotNull MethodsChain right) {
    if (left.size() == 0) {
      return CompareResult.RIGHT_CONTAINS_LEFT;
    }
    if (right.size() == 0) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    Iterator<PsiMethod[]> leftIterator = left.myRevertedPath.iterator();
    Iterator<PsiMethod[]> rightIterator = right.myRevertedPath.iterator();

    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      PsiMethod[] thisNext = leftIterator.next();
      PsiMethod[] thatNext = rightIterator.next();
      if (!lookSimilar(thisNext, thatNext)) {
        return CompareResult.NOT_EQUAL;
      }
    }
    if (leftIterator.hasNext() && !rightIterator.hasNext()) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    if (!leftIterator.hasNext() && rightIterator.hasNext()) {
      return CompareResult.RIGHT_CONTAINS_LEFT;
    }

    return CompareResult.EQUAL;
  }

  public enum CompareResult {
    LEFT_CONTAINS_RIGHT,
    RIGHT_CONTAINS_LEFT,
    EQUAL,
    NOT_EQUAL
  }

  private static boolean lookSimilar(PsiMethod[] methods1, PsiMethod[] methods2) {
    PsiMethod repr1 = methods1[0];
    PsiMethod repr2 = methods2[0];
    if (repr1.hasModifierProperty(PsiModifier.STATIC) || repr2.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!repr1.getName().equals(repr2.getName()) ||
        repr1.getParameterList().getParametersCount() != repr2.getParameterList().getParametersCount()) {
      return false;
    }
    Set<PsiMethod> methodSet1 = ContainerUtil.newHashSet(methods1);
    Set<PsiMethod> methodSet2 = ContainerUtil.newHashSet(methods2);
    if (ContainerUtil.intersects(methodSet1, methodSet2)) return true;

    Set<PsiMethod> deepestSupers1 = methodSet1.stream().flatMap(m -> Arrays.stream(m.findDeepestSuperMethods())).collect(Collectors.toSet());
    return methodSet2.stream().flatMap(m -> Arrays.stream(m.findDeepestSuperMethods())).anyMatch(deepestSupers1::contains);
  }
}

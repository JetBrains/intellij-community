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

import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;


public class CallChain {
  @NotNull
  private final ChainOperation[] myReverseOperations;
  private final MethodIncompleteSignature mySignature;
  private final int myWeight;
  private final PsiClass myQualifierClass;

  @Nullable
  public static CallChain create(@NotNull MethodIncompleteSignature signature,
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
    return new CallChain(qualifier, new ChainOperation[] {new ChainOperation.MethodCall(methods)}, signature, weight);
  }

  public CallChain(@NotNull PsiClass qualifierClass,
                   @NotNull ChainOperation[] reverseOperations,
                   MethodIncompleteSignature signature,
                   int weight) {
    myQualifierClass = qualifierClass;
    myReverseOperations = reverseOperations;
    mySignature = signature;
    myWeight = weight;
  }

  @NotNull
  public MethodIncompleteSignature getHeadSignature() {
    return mySignature;
  }

  public int length() {
    return myReverseOperations.length;
  }

  public PsiClass getQualifierClass() {
    return myQualifierClass;
  }

  @NotNull
  public PsiMethod[] getFirst() {
    return ((ChainOperation.MethodCall) myReverseOperations[0]).getCandidates();
  }

  public ChainOperation[] getPath() {
    return ArrayUtil.reverseArray(myReverseOperations);
  }

  public int getChainWeight() {
    return myWeight;
  }

  public CallChain continuation(@NotNull MethodIncompleteSignature signature,
                                int weight,
                                @NotNull ChainCompletionContext context) {
    CallChain head = create(signature, weight, context);
    if (head == null) return null;

    ChainOperation[] newReverseOperations = new ChainOperation[length() + 1];
    System.arraycopy(myReverseOperations, 0, newReverseOperations, 0, myReverseOperations.length);
    newReverseOperations[length()] = head.getPath()[0];
    return new CallChain(head.getQualifierClass(), newReverseOperations, head.getHeadSignature(), Math.min(weight, getChainWeight()));
  }

  @Override
  public String toString() {
    ChainOperation[] path = getPath();
    return Arrays.toString(path) + " on " + myQualifierClass.getName();
  }

  @SuppressWarnings("ConstantConditions")
  public static CompareResult compare(@NotNull CallChain left, @NotNull CallChain right) {
    if (left.length() == 0 || right.length() == 0) {
      throw new IllegalStateException("chains can't be empty");
    }

    int leftCurrentIdx = 0;
    int rightCurrentIdx = 0;

    while (leftCurrentIdx < left.length() && rightCurrentIdx < right.length()) {
      ChainOperation thisNext = left.myReverseOperations[leftCurrentIdx];
      ChainOperation thatNext = right.myReverseOperations[leftCurrentIdx];
      if (!lookSimilar(thisNext, thatNext)) {
        return CompareResult.NOT_EQUAL;
      }
      leftCurrentIdx++;
      rightCurrentIdx++;
    }
    if (leftCurrentIdx < left.length() && rightCurrentIdx == right.length()) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    if (leftCurrentIdx == left.length() && rightCurrentIdx < right.length()) {
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

  static boolean lookSimilar(ChainOperation op1, ChainOperation op2) {
    if (op1 instanceof ChainOperation.TypeCast || op2 instanceof ChainOperation.TypeCast) return false;

    PsiMethod[] methods1 = ((ChainOperation.MethodCall)op1).getCandidates();
    PsiMethod[] methods2 = ((ChainOperation.MethodCall)op2).getCandidates();

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

package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.reverse;

/**
 * @author Dmitry Batkovich
 */
public class MethodsChain {
  private final List<PsiMethod[]> myRevertedPath;
  private final int myWeight;

  public MethodsChain(final PsiMethod[] methods, final int weight) {
    this(ContainerUtil.<PsiMethod[]>newArrayList(methods), weight);
  }

  public MethodsChain(final List<PsiMethod[]> revertedPath, final int weight) {
    myRevertedPath = revertedPath;
    myWeight = weight;
  }

  public int size() {
    return myRevertedPath.size();
  }

  public boolean isStaticChain() {
    return myRevertedPath.get(myRevertedPath.size() - 1)[0].hasModifierProperty(PsiModifier.STATIC);
  }

  @Nullable
  public PsiClass getFirstQualifierClass() {
    return myRevertedPath.isEmpty() ? null : myRevertedPath.get(myRevertedPath.size() - 1)[0].getContainingClass();
  }

  @Nullable
  public PsiMethod getOneOfFirst() {
    return (myRevertedPath.isEmpty() || myRevertedPath.get(0).length == 0) ? null : myRevertedPath.get(myRevertedPath.size() - 1)[0];
  }

  public List<PsiMethod[]> getPath() {
    return reverse(myRevertedPath);
  }

  public int getChainWeight() {
    return myWeight;
  }

  public MethodsChain addEdge(final PsiMethod[] psiMethods) {
    final List<PsiMethod[]> newRevertedPath = new ArrayList<PsiMethod[]>(myRevertedPath.size() + 1);
    newRevertedPath.addAll(myRevertedPath);
    newRevertedPath.add(psiMethods);
    return new MethodsChain(newRevertedPath, myWeight);
  }

  /**
   * checking only method names
   */
  public boolean weakContains(final MethodsChain otherChain) {
    if (otherChain.myRevertedPath.isEmpty()) {
      return true;
    }
    if (myRevertedPath.isEmpty()) {
      return false;
    }
    final Iterator<PsiMethod[]> otherChainIterator = otherChain.myRevertedPath.iterator();
    String otherChainCurrentName = otherChainIterator.next()[0].getName();
    boolean checkingStarted = false;
    for (final PsiMethod[] methods : myRevertedPath) {
      final String thisCurrentName = methods[0].getName();
      if (!checkingStarted && thisCurrentName.equals(otherChainCurrentName)) {
        checkingStarted = true;
      }
      if (checkingStarted) {
        if (otherChainIterator.hasNext()) {
          otherChainCurrentName = otherChainIterator.next()[0].getName();
          if (!otherChainCurrentName.equals(thisCurrentName)) {
            return false;
          }
        } else {
          return false;
        }
      }
    }
    return !otherChainIterator.hasNext();
  }

  @Override
  public String toString() {
    return StringUtil.join(myRevertedPath, "<-");
  }

  public static Set<Set<String>> edgeCombinations(final Set<Set<String>> oldCombinations,
                                                  final MethodsChain methodsChain) {
    if (oldCombinations.isEmpty()) {
      final Set<Set<String>> result = new HashSet<Set<String>>(methodsChain.myRevertedPath.size());
      for (final PsiMethod[] e : methodsChain.myRevertedPath) {
        final Set<String> set = new HashSet<String>();
        set.add(e[0].getName());
        result.add(set);
      }
      return result;
    } else {
      final Set<Set<String>> newTail = new HashSet<Set<String>>(oldCombinations.size() * methodsChain.size());
      for (final PsiMethod[] e : methodsChain.myRevertedPath) {
        final String methodName = e[0].getName();
        for (final Set<String> tailSet : oldCombinations) {
          final Set<String> newSet = new HashSet<String>(tailSet);
          newSet.add(methodName);
          if (!oldCombinations.contains(newSet)) {
            newTail.add(newSet);
          }
        }
      }
      return newTail;
    }
  }

  @SuppressWarnings("ConstantConditions")
  public static CompareResult compare(final MethodsChain left, final MethodsChain right) {
    if (left.size() == 0) {
      return CompareResult.RIGHT_CONTAINS_LEFT;
    }
    if (right.size() == 0) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    final Iterator<PsiMethod[]> leftIterator = left.myRevertedPath.iterator();
    final Iterator<PsiMethod[]> rightIterator = right.myRevertedPath.iterator();

    final PsiManager psiManager = PsiManager.getInstance(left.getFirstQualifierClass().getProject());
    while (leftIterator.hasNext() && rightIterator.hasNext()) {
      final PsiMethod thisNext = leftIterator.next()[0];
      final PsiMethod thatNext = rightIterator.next()[0];
      if (((thisNext.isConstructor() != thatNext.isConstructor()))
          || !thisNext.getName().equals(thatNext.getName())
          || !psiManager.areElementsEquivalent(thisNext.getContainingClass(), thatNext.getContainingClass())) {
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
}

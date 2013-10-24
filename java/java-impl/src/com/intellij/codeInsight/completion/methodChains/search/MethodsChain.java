package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.codeInsight.completion.methodChains.completion.context.ChainCompletionContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
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
  //
  // chain qualifier class could be different with method.getContainingClass()
  private final String myQualifierClassName;

  public MethodsChain(final PsiMethod[] methods, final int weight, final String qualifierClassName) {
    this(ContainerUtil.<PsiMethod[]>newArrayList(methods), weight, qualifierClassName);
  }

  public MethodsChain(final List<PsiMethod[]> revertedPath, final int weight, final String qualifierClassName) {
    myRevertedPath = revertedPath;
    myWeight = weight;
    myQualifierClassName = qualifierClassName;
  }

  public int size() {
    return myRevertedPath.size();
  }

  public String getQualifierClassName() {
    return myQualifierClassName;
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

  public MethodsChain addEdge(final PsiMethod[] psiMethods, final String newQualifierClassName, final int newWeight) {
    final List<PsiMethod[]> newRevertedPath = new ArrayList<PsiMethod[]>(myRevertedPath.size() + 1);
    newRevertedPath.addAll(myRevertedPath);
    newRevertedPath.add(psiMethods);
    return new MethodsChain(newRevertedPath, newWeight, newQualifierClassName);
  }

  @Override
  public String toString() {
    return StringUtil.join(myRevertedPath, "<-");
  }

  @SuppressWarnings("ConstantConditions")
  public static CompareResult compare(final MethodsChain left, final MethodsChain right, final ChainCompletionContext context) {
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
      if (((thisNext.isConstructor() != thatNext.isConstructor()))
          || !thisNext.getName().equals(thatNext.getName())) {
        return CompareResult.NOT_EQUAL;
      }
    }
    if (leftIterator.hasNext() && !rightIterator.hasNext()) {
      return CompareResult.LEFT_CONTAINS_RIGHT;
    }
    if (!leftIterator.hasNext() && rightIterator.hasNext()) {
      return CompareResult.RIGHT_CONTAINS_LEFT;
    }


    final PsiClass leftQualifier = JavaPsiFacade.getInstance(context.getProject()).findClass(left.getQualifierClassName(), context.getResolveScope());
    final PsiClass rightQualifier = JavaPsiFacade.getInstance(context.getProject()).findClass(left.getQualifierClassName(), context.getResolveScope());
    return hasBaseClass(leftQualifier, rightQualifier, PsiManager.getInstance(context.getProject())) ? CompareResult.EQUAL : CompareResult.NOT_EQUAL;
  }

  private static boolean hasBaseClass(final PsiClass left, final PsiClass right, final PsiManager psiManager) {
    //todo so slow
    final Set<PsiClass> leftSupers = InheritanceUtil.getSuperClasses(left);
    final Set<PsiClass> rightSupers = InheritanceUtil.getSuperClasses(right);
    for (final PsiClass leftSuper : leftSupers) {
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(leftSuper.getQualifiedName()) && rightSupers.contains(leftSuper)) {
        return true;
      }
    }
    return false;
  }

  public enum CompareResult {
    LEFT_CONTAINS_RIGHT,
    RIGHT_CONTAINS_LEFT,
    EQUAL,
    NOT_EQUAL
  }
}

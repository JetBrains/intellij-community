package com.intellij.compilerOutputIndex.impl.singleton;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Dmitry Batkovich
 */
public class MethodShortSignatureWithWeight {
  private final MethodShortSignature myMethodShortSignature;
  private final int myWeight;

  public MethodShortSignatureWithWeight(final MethodShortSignature methodShortSignature, final int weight) {
    myMethodShortSignature = methodShortSignature;
    myWeight = weight;
  }

  public MethodShortSignature getMethodShortSignature() {
    return myMethodShortSignature;
  }

  public int getWeight() {
    return myWeight;
  }

  public static Comparator<MethodShortSignatureWithWeight> COMPARATOR = new Comparator<MethodShortSignatureWithWeight>() {
    @Override
    public int compare(final MethodShortSignatureWithWeight o1, final MethodShortSignatureWithWeight o2) {
      return o1.getWeight() - o2.getWeight();
    }
  }   ;

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodShortSignatureWithWeight that = (MethodShortSignatureWithWeight) o;

    if (myWeight != that.myWeight) return false;
    if (!myMethodShortSignature.equals(that.myMethodShortSignature)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethodShortSignature.hashCode();
    result = 31 * result + myWeight;
    return result;
  }
}

package com.intellij.codeInsight.completion.methodChains.search;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class WeightAware<V> implements Comparable<WeightAware<V>> {
  private final V myUnderlying;
  private final int myWeight;

  public WeightAware(final V underlying, final int weight) {
    myUnderlying = underlying;
    myWeight = weight;
  }

  public V getUnderlying() {
    return myUnderlying;
  }

  public int getWeight() {
    return myWeight;
  }

  @Override
  public int compareTo(@NotNull final WeightAware<V> that) {
    final int sub = -getWeight() + that.getWeight();
    if (sub != 0) {
      return sub;
    }
    return myUnderlying.hashCode() - that.myUnderlying.hashCode();
  }
}

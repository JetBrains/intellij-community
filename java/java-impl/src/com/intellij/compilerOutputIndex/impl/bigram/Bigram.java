package com.intellij.compilerOutputIndex.impl.bigram;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class Bigram<E> extends Pair<E, E> {
  public Bigram(@NotNull final E first, @NotNull final E second) {
    super(first, second);
  }

  public Bigram<E> swap() {
    return new Bigram<E>(second, first);
  }

  @Override
  public String toString() {
    return String.format("%s - %s", first, second);
  }
}

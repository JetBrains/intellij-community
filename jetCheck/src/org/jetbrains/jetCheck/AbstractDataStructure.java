package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * @author peter
 */
abstract class AbstractDataStructure implements DataStructure {
  protected final StructureNode node;
  protected final int sizeHint;

  AbstractDataStructure(StructureNode node, int sizeHint) {
    this.node = node;
    this.sizeHint = sizeHint;
  }

  int childSizeHint() {
    return Math.max(1, sizeHint - 1);
  }

  @Override
  public int getSizeHint() {
    return sizeHint;
  }

  abstract int drawInt(@NotNull IntDistribution distribution);

  int suggestCollectionSize() {
    return drawInt(IntDistribution.uniform(0, getSizeHint()));
  }

  abstract <T> T generateNonShrinkable(@NotNull Generator<T> generator);

  abstract <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<? super T> condition);

}

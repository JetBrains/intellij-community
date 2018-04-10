package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * @author peter
 */
public interface DataStructure {

  default int drawInt() {
    return drawInt(BoundedIntDistribution.ALL_INTS);
  }
  
  int drawInt(@NotNull IntDistribution distribution);

  /**
   * @return a non-negative number used by various generators to guide the sizes of structures (e.g. collections) they create.
   * The sizes need not be exactly equal to this hint, but in average bigger hints should in average correspond to bigger structures. When generators invoke other generators, the size hint of the structure used by called generators is
   * generally less than the original one's.
   */
  int getSizeHint();
  
  default int suggestCollectionSize() {
    return drawInt(IntDistribution.uniform(0, getSizeHint()));
  }

  /** Runs the given generator on this data structure and returns the result */
  <T> T generate(@NotNull Generator<T> generator);
  
  /** @see Generator#noShrink() */
  <T> T generateNonShrinkable(@NotNull Generator<T> generator);

  /** @see Generator#suchThat */
  <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<T> condition);
}

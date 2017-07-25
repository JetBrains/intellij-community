package jetCheck;

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
   * The sizes need not be exactly equal to this hint, but in average bigger hints should in average correspond to bigger structures. When generators invoke other generators using {@link #subStructure}, the size hint of the sub-structure is
   * generally less than the parent's one.
   */
  int getSizeHint();
  
  default int suggestCollectionSize() {
    return drawInt(IntDistribution.geometric(getSizeHint()));
  }

  @NotNull
  DataStructure subStructure();
  
  <T> T generateNonShrinkable(@NotNull Generator<T> generator);

  <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<T> condition);
}

package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

/**
 * A context for {@link Generator}s. Primitive generators (e.g. {@link Generator#integers} know how to obtain
 * random data from it, other generators build more complex values on top of that, by running {@link #generate(Generator)} recursively.
 */
public interface DataStructure {

  /**
   * @return a non-negative number used by various generators to guide the sizes of structures (e.g. collections) they create.
   * The sizes need not be exactly equal to this hint, but in average bigger hints should in average correspond to bigger structures. When generators invoke other generators, the size hint of the structure used by called generators is
   * generally less than the original one's.
   */
  int getSizeHint();
  
  /** Runs the given generator on a data sub-structure of this structure and returns the result */
  <T> T generate(@NotNull Generator<T> generator);
  
}

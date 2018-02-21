package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * An entry point to property-based testing. The main usage pattern: {@code PropertyChecker.forAll(generator).shouldHold(property)}.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class PropertyChecker<T> {
  static final int DEFAULT_MAX_SIZE_HINT = 100;
  private final Generator<T> generator;
  private long globalSeed = new Random().nextLong();
  @Nullable IntSource serializedData;
  private IntUnaryOperator sizeHintFun = iteration -> (iteration - 1) % DEFAULT_MAX_SIZE_HINT + 1;
  private int iterationCount = 100;
  private boolean silent;

  private PropertyChecker(Generator<T> generator) {
    this.generator = generator;
  }

  /**
   * Creates a property checker for the given generator. It can be further customized using {@code with*}-methods, 
   * and should finally used for property to check via {@link #shouldHold(Predicate)} call.
   */
  public static <T> PropertyChecker<T> forAll(Generator<T> generator) {
    return new PropertyChecker<>(generator);
  }

  /**
   * This function allows to start the test with a fixed random seed. It's useful to reproduce some previous test run and debug it.
   * @param seed A random seed to use for the first iteration.
   *             The following iterations will use other, pseudo-random seeds, but still derived from this one.
   * @return this PropertyChecker
   * @deprecated To catch your attention. It's fine to call this method during test debugging, but it should not be committed to version control
   * and used in regression tests, because any changes in the test itself or the framework can render the passed argument obsolete.
   * For regression testing, it's recommended to code the failing scenario explicitly.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  public PropertyChecker<T> withSeed(long seed) {
    if (serializedData != null) {
      System.err.println("withSeed ignored, because 'rechecking' is used");
      return this;
    }

    globalSeed = seed;
    return this;
  }

  /**
   * @param iterationCount the number of iterations to try. By default it's 100.
   * @return this PropertyChecker
   */
  public PropertyChecker<T> withIterationCount(int iterationCount) {
    if (serializedData != null) {
      System.err.println("withIterationCount ignored, because 'rechecking' is used");
      return this;
    }
    this.iterationCount = iterationCount;
    return this;
  }

  /**
   * @param sizeHintFun a function determining how size hint should be distributed depending on the iteration number.
   *                    By default the size hint will be 1 in the first iteration, 2 in the second one, and so on until 100,
   *                    then again 1,...,100,1,...,100, etc.
   * @return this PropertyChecker
   * @see DataStructure#getSizeHint() 
   */
  public PropertyChecker<T> withSizeHint(@NotNull IntUnaryOperator sizeHintFun) {
    if (serializedData != null) {
      System.err.println("withSizeHint ignored, because 'rechecking' is used");
      return this;
    }

    this.sizeHintFun = sizeHintFun;
    return this;
  }

  /**
   * Suppresses all output from the testing infrastructure during property check and shrinking
   * @return this PropertyChecker
   */
  public PropertyChecker<T> silently() {
    this.silent = true;
    return this;
  }

  /**
   * Checks the property within a single iteration by using specified seed and size hint. Useful to debug the test after it's failed, if {@link #rechecking} isn't enough (e.g. due to unforeseen side effects).
   * @deprecated To catch your attention. It's fine to call this method during test debugging, but it should not be committed to version control
   * and used in regression tests, because any changes in the test itself or the framework can render the passed arguments obsolete.
   * For regression testing, it's recommended to code the failing scenario explicitly.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  public PropertyChecker<T> recheckingIteration(long seed, int sizeHint) {
    return withSeed(seed).withSizeHint(whatever -> sizeHint).withIterationCount(1);
  }

  /**
   * Checks the property within a single iteration by using specified underlying data. Useful to debug the test after it's failed.
   * @param serializedData the data used for running generators in serialized form, as printed by {@link PropertyFailure} exception.
   * @deprecated To catch your attention. It's fine to call this method during test debugging, but it should not be committed to version control
   * and used in regression tests, because any changes in the test itself or the framework can render the passed argument obsolete.
   * For regression testing, it's recommended to code the failing scenario explicitly.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  public PropertyChecker<T> rechecking(@NotNull String serializedData) {
    this.iterationCount = 1;
    DataSerializer.deserializeInto(serializedData, this);
    return this;
  }

  /**
   * Checks that the given property returns {@code true} and doesn't throw exceptions by running the generator and the property
   * on random data repeatedly for some number of times (see {@link #withIterationCount(int)}).
   */
  public void shouldHold(@NotNull Predicate<T> property) {
    Iteration<T> iteration = new CheckSession<>(serializedData == null ? generator : generator.noShrink(), 
                                                property, globalSeed, iterationCount, sizeHintFun, silent, 
                                                serializedData).firstIteration();
    while (iteration != null) {
      iteration = iteration.performIteration();
    }
  }

}


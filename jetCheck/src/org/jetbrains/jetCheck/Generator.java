package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A generator for objects based on random data from {@link DataStructure}.<p/>
 * 
 * Generators for standard types can be obtained using static methods of this class (e.g. {@link #integers}, {@link #listsOf} etc).
 * Generators for custom types can be created by deriving them from standard types (e.g. via {@link #map}, {@link #flatMap}, {@link #zipWith}) or by writing your own from scratch ({@link #from(Function)}).  
 */
public class Generator<T> {
  private final Function<DataStructure, T> myFunction;

  Generator(Function<DataStructure, T> function) {
    myFunction = function;
  }

  /**
   * Creates a generator from a custom function, that creates objects of the given type based on the data from {@link DataStructure}.
   * The generator may call {@link DataStructure#drawInt} methods directly (and interpret those ints in any way it wishes),
   * or (preferably) invoke other generators using {@link DataStructure#generate(Generator)}.<p/>
   * 
   * When a property is falsified, the DataStructure is attempted to be minimized, and the generator will be run on
   * ever "smaller" versions of it, this enables automatic minimization on all kinds of generated types.<p/>
   * 
   * To ensure test reproducibility during re-run or minimization phase,
   * the result of the generators should only depend on the DataStructure. Generators should not have any side effects
   * or depend on the outside world. Generators may have internal mutable state accessible to other (nested) generators,
   * but that's error-prone, difficult and computationally expensive to minimize. If you still think you need that,
   * please see {@link ImperativeCommand} for potentially more convenient way of testing stateful systems.
   */
  @NotNull
  public static <T> Generator<T> from(@NotNull Function<DataStructure, T> function) {
    return new Generator<>(function);
  }

  Function<DataStructure, T> getGeneratorFunction() {
    return myFunction;
  }

  /**
   * Invokes "this" generator, and then applies the given function to transform the generated value in any way.
   * The function should not depend on anything besides its argument.
   */
  public <V> Generator<V> map(@NotNull Function<T,V> fun) {
    return from(data -> fun.apply(myFunction.apply(data)));
  }

  /**
   * Invokes "this" generator, and then applies the given function to provide a new generator that additionally
   * depends on the generated value.
   * The function should not depend on anything besides its argument.
   */
  public <V> Generator<V> flatMap(@NotNull Function<T,Generator<V>> fun) {
    return from(data -> {
      T value = data.generate(this);
      Generator<V> result = fun.apply(value);
      if (result == null) throw new NullPointerException(fun + " returned null on " + value);
      return data.generate(result);
    });
  }

  /**
   * Turns off automatic minimization for the data produced by this generator (and its components, if any).
   * This can be useful to speed up minimization by not wasting time on shrinking objects where it makes no sense. 
   */
  public Generator<T> noShrink() {
    return from(data -> data.generateNonShrinkable(this));
  }

  /**
   * Attempts to generate the value several times, until one comes out that satisfies the given condition. That value is returned as generator result.
   * Results of all previous attempts are discarded. During shrinking, the underlying data structures from those attempts
   * won't be used for re-running generation, so be careful that those attempts don't leave any traces of themselves 
   * (e.g. side effects, even ones internal to an outer generator).<p/> 
   * 
   * If the condition still fails after a large number of attempts, data generation is stopped prematurely and {@link CannotSatisfyCondition} exception is thrown.<p/>
   * 
   * This method is useful to avoid infrequent corner cases (e.g. {@code integers().suchThat(i -> i != 0)}). 
   * To eliminate large portions of search space, this strategy might prove ineffective 
   * and result in generator failure due to inability to come up with satisfying examples 
   * (e.g. {@code integers().suchThat(i -> i > 0 && i <= 10)} 
   * where the condition would be {@code true} in just 10 of about 4 billion times). In such cases, please consider changing the generator instead of using {@code suchThat}.
   */
  public Generator<T> suchThat(@NotNull Predicate<T> condition) {
    return from(data -> data.generateConditional(this, condition));
  }

  // ------------------------------------
  //        common generators
  // ------------------------------------

  /** A generator that always returns the same value */
  public static <T> Generator<T> constant(T value) {
    return from(data -> value);
  }

  /** A generator that returns one of the given values with equal probability */ 
  @SafeVarargs
  public static <T> Generator<T> sampledFrom(T... values) {
    return sampledFrom(Arrays.asList(values));
  }

  /** A generator that returns one of the given values with equal probability */
  public static <T> Generator<T> sampledFrom(List<T> values) {
    return anyOf(values.stream().map(Generator::constant).collect(Collectors.toList()));
  }

  /** Delegates to one of the given generators with equal probability */
  @SafeVarargs 
  public static <T> Generator<T> anyOf(Generator<? extends T>... alternatives) {
    return anyOf(Arrays.asList(alternatives));
  }

  /** Delegates to one of the given generators with equal probability */
  public static <T> Generator<T> anyOf(List<Generator<? extends T>> alternatives) {
    if (alternatives.isEmpty()) throw new IllegalArgumentException("No alternatives to choose from");
    return from(data -> {
      int index = data.generateNonShrinkable(integers(0, alternatives.size() - 1));
      return data.generate(alternatives.get(index));
    });
  }
 
  /** Delegates one of the two generators with probability corresponding to their weights */
  public static <T> FrequencyGenerator<T> frequency(int weight1, Generator<? extends T> alternative1,
                                                    int weight2, Generator<? extends T> alternative2) {
    return new FrequencyGenerator<>(weight1, alternative1, weight2, alternative2);
  }

  /** Delegates one of the three generators with probability corresponding to their weights */
  public static <T> Generator<T> frequency(int weight1, Generator<? extends T> alternative1, 
                                           int weight2, Generator<? extends T> alternative2,
                                           int weight3, Generator<? extends T> alternative3) {
    return frequency(weight1, alternative1, weight2, alternative2).with(weight3, alternative3);
  }

  /** Gets the data from two generators and invokes the given function to produce a result based on the two generated values. */
  public static <A,B,C> Generator<C> zipWith(Generator<A> gen1, Generator<B> gen2, BiFunction<A,B,C> zip) {
    return from(data -> zip.apply(data.generate(gen1), data.generate(gen2)));
  }

  /**
   * A fixed-point combinator to easily create recursive generators by giving a name to the whole generator and allowing to reuse it inside itself. For example, a recursive tree generator could be defined as follows by binding itself to the name {@code nodes}:
   * <pre>
   * Generator<Node> gen = Generator.recursive(<b>nodes</b> -> Generator.anyOf(
   *   Generator.constant(new Leaf()),
   *   Generator.listsOf(<b>nodes</b>).map(children -> new Composite(children))))  
   * </pre>
   * @return the generator returned from the passed function
   */
  @NotNull
  public static <T> Generator<T> recursive(@NotNull Function<Generator<T>, Generator<T>> createGenerator) {
    AtomicReference<Generator<T>> ref = new AtomicReference<>();
    Generator<T> result = from(data -> ref.get().getGeneratorFunction().apply(data));
    ref.set(createGenerator.apply(result));
    return result;
  }

  /** A generator that returns 'true' or 'false' */
  public static Generator<Boolean> booleans() {
    return integers(0, 1).map(i -> i == 1);
  }

  // char generators

  /** Generates characters in the given range (both ends inclusive) */
  public static Generator<Character> charsInRange(char min, char max) {
    return integers(min, max).map(i -> (char)i.intValue()).noShrink();
  }

  /** Generates ASCII characters excluding the system ones (lower than 32) */
  public static Generator<Character> asciiPrintableChars() {
    return charsInRange((char)32, (char)126);
  }

  /** Generates uppercase latin letters */
  public static Generator<Character> asciiUppercaseChars() {
    return charsInRange('A', 'Z');
  }

  /** Generates lowercase latin letters */
  public static Generator<Character> asciiLowercaseChars() {
    return charsInRange('a', 'z');
  }

  /** Generates latin letters, with a preference for lowercase ones */
  public static Generator<Character> asciiLetters() {
    return frequency(9, asciiLowercaseChars(), 1, asciiUppercaseChars()).noShrink();
  }

  /** Generates decimal digits */
  public static Generator<Character> digits() {
    return charsInRange('0', '9');
  }

  // strings

  /** Generates (possibly empty) random strings consisting of the given characters (provided as a string) */
  public static Generator<String> stringsOf(@NotNull String possibleChars) {
    List<Character> chars = IntStream.range(0, possibleChars.length()).mapToObj(possibleChars::charAt).collect(Collectors.toList());
    return stringsOf(sampledFrom(chars));
  }

  /** Generates (possibly empty) random strings consisting of characters provided by the given generator */
  public static Generator<String> stringsOf(@NotNull Generator<Character> charGen) {
    return listsOf(charGen).map(chars -> {
      StringBuilder sb = new StringBuilder();
      chars.forEach(sb::append);
      return sb.toString();
    });
  }

  /** Generates random strings consisting ASCII letters and digit and starting with a letter */
  public static Generator<String> asciiIdentifiers() {
    return stringsOf(frequency(50, asciiLetters(),
                               5, digits(),
                               1, constant('_')))
      .suchThat(s -> s.length() > 0 && !Character.isDigit(s.charAt(0)));
  }


  // numbers

  /** Generates any integers */
  public static Generator<Integer> integers() {
    return from(data -> data.drawInt());
  }

  public static Generator<Integer> naturals() {
    return integers(0, Integer.MAX_VALUE);
  }

  /** Generates integers uniformly distributed in the given range (both ends inclusive) */
  public static Generator<Integer> integers(int min, int max) {
    return integers(IntDistribution.uniform(min, max));
  }

  /** Generates integers with the given distribution */
  public static Generator<Integer> integers(@NotNull IntDistribution distribution) {
    return from(data -> data.drawInt(distribution));
  }

  /** Generates any doubles, including infinities and NaN */
  public static Generator<Double> doubles() {
    return from(data -> {
      long i1 = data.drawInt();
      long i2 = data.drawInt();
      return Double.longBitsToDouble((i1 << 32) + (i2 & 0xffffffffL));
    });
  }

  // lists

  /** Generates (possibly empty) lists of values produced by the given generator */
  public static <T> Generator<List<T>> listsOf(Generator<T> itemGenerator) {
    return from(data -> generateList(itemGenerator, data, data.suggestCollectionSize()));
  }

  /** Generates non-empty lists of values produced by the given generator */
  public static <T> Generator<List<T>> nonEmptyLists(Generator<T> itemGenerator) {
    return listsOf(itemGenerator).suchThat(l -> !l.isEmpty());
  }

  /** Generates lists of values produced by the given generator. The list length is determined by the given distribution. */
  public static <T> Generator<List<T>> listsOf(IntDistribution length, Generator<T> itemGenerator) {
    return from(data -> generateList(itemGenerator, data, data.drawInt(length)));
  }

  private static <T> List<T> generateList(Generator<T> itemGenerator, DataStructure data, int size) {
    List<T> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(data.generate(itemGenerator));
    }
    return Collections.unmodifiableList(list);
  }
}

package jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.*;
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
   * or invoke other generators using {@link #generateValue(DataStructure)}.<p/>
   * 
   * When a property is falsified, the DataStructure is attempted to be minimized, and the generator will be run on
   * ever "smaller" versions of it, this enables automatic minimization on all kinds of generated types.<p/>
   * 
   * To ensure test reproducibility during re-run or minimization phase, generators must not have any internal state.
   * Their result should only depend on the DataStructure.
   */
  @NotNull
  public static <T> Generator<T> from(@NotNull Function<DataStructure, T> function) {
    return new Generator<>(function);
  }

  /**
   * Generates a value inside the given data structure.
   */
  public T generateValue(@NotNull DataStructure data) {
    return myFunction.apply(data.subStructure());
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
      T value = generateValue(data);
      Generator<V> result = fun.apply(value);
      if (result == null) throw new NullPointerException(fun + " returned null on " + value);
      return result.generateValue(data);
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
   * Skips all generated data that doesn't satisfy the given condition. Useful to avoid infrequent corner cases.
   * If the condition fails too often, data generation is stopped prematurely due to inability to produce the data.<p/>
   * 
   * To eliminate large portions of search space, consider changing the generator instead of using {@code suchThat}.
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
      return alternatives.get(index).generateValue(data);
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
    return from(data -> zip.apply(gen1.generateValue(data), gen2.generateValue(data)));
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

  /** Generates integers in the given range (both ends inclusive) */
  public static Generator<Integer> integers(int min, int max) {
    IntDistribution distribution = IntDistribution.uniform(min, max);
    return from(data -> data.drawInt(distribution));
  }

  /** Generates any doubles, including infinities and NaN */
  public static Generator<Double> doubles() {
    return from(data -> {
      long i1 = data.drawInt();
      long i2 = data.drawInt();
      return Double.longBitsToDouble((i1 << 32) + i2);
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
      list.add(itemGenerator.generateValue(data));
    }
    return Collections.unmodifiableList(list);
  }
}

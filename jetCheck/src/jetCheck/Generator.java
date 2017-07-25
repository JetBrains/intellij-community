package jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A generator for objects based on random data from {@link DataStructure}.
 *
 * @author peter
 */
public final class Generator<T> {
  private final Function<DataStructure, T> myFunction;

  private Generator(Function<DataStructure, T> function) {
    myFunction = function;
  }

  @NotNull
  public static <T> Generator<T> from(@NotNull Function<DataStructure, T> function) {
    return new Generator<>(function);
  }

  public T generateValue(@NotNull DataStructure data) {
    return myFunction.apply(data.subStructure());
  }

  Function<DataStructure, T> getGeneratorFunction() {
    return myFunction;
  }

  public <V> Generator<V> map(@NotNull Function<T,V> fun) {
    return from(data -> fun.apply(myFunction.apply(data)));
  }
  
  public <V> Generator<V> flatMap(@NotNull Function<T,Generator<V>> fun) {
    return from(data -> {
      T value = generateValue(data);
      Generator<V> result = fun.apply(value);
      if (result == null) throw new NullPointerException(fun + " returned null on " + value);
      return result.generateValue(data);
    });
  }
  
  public Generator<T> noShrink() {
    return from(data -> data.generateNonShrinkable(this));
  }

  public Generator<T> suchThat(@NotNull Predicate<T> condition) {
    return from(data -> data.generateConditional(this, condition));
  }

  // ------------------------------------
  //        common generators
  // ------------------------------------

  public static <T> Generator<T> constant(T value) {
    return from(data -> value);
  }

  @SafeVarargs
  public static <T> Generator<T> sampledFrom(T... values) {
    return sampledFrom(Arrays.asList(values));
  }

  public static <T> Generator<T> sampledFrom(List<T> values) {
    return anyOf(values.stream().map(Generator::constant).collect(Collectors.toList()));
  }

  @SafeVarargs 
  public static <T> Generator<T> anyOf(Generator<? extends T>... alternatives) {
    return anyOf(Arrays.asList(alternatives));
  }

  public static <T> Generator<T> anyOf(List<Generator<? extends T>> alternatives) {
    if (alternatives.isEmpty()) throw new IllegalArgumentException("No alternatives to choose from");
    return from(data -> {
      int index = data.generateNonShrinkable(integers(0, alternatives.size() - 1));
      return alternatives.get(index).generateValue(data);
    });
  }
 
  public static <T> Generator<T> frequency(int weight1, Generator<? extends T> alternative1, 
                                           int weight2, Generator<? extends T> alternative2) {
    Map<Integer, Generator<? extends T>> alternatives = new HashMap<>();
    alternatives.put(weight1, alternative1);
    alternatives.put(weight2, alternative2);
    return frequency(alternatives);
  }

  public static <T> Generator<T> frequency(int weight1, Generator<? extends T> alternative1, 
                                           int weight2, Generator<? extends T> alternative2,
                                           int weight3, Generator<? extends T> alternative3) {
    Map<Integer, Generator<? extends T>> alternatives = new HashMap<>();
    alternatives.put(weight1, alternative1);
    alternatives.put(weight2, alternative2);
    alternatives.put(weight3, alternative3);
    return frequency(alternatives);
  }
  
  public static <T> Generator<T> frequency(Map<Integer, Generator<? extends T>> alternatives) {
    List<Integer> weights = new ArrayList<>(alternatives.keySet());
    IntDistribution distribution = IntDistribution.frequencyDistribution(weights);
    return from(data -> alternatives.get(weights.get(data.drawInt(distribution))).generateValue(data));
  }

  public static <A,B,C> Generator<C> zipWith(Generator<A> gen1, Generator<B> gen2, BiFunction<A,B,C> zip) {
    return from(data -> zip.apply(gen1.generateValue(data), gen2.generateValue(data)));
  }

  public static Generator<Boolean> booleans() {
    return integers(0, 1).map(i -> i == 1);
  }

  // char generators

  public static Generator<Character> charsInRange(char min, char max) {
    return integers(min, max).map(i -> (char)i.intValue()).noShrink();
  }

  public static Generator<Character> asciiPrintableChars() {
    return charsInRange((char)32, (char)126);
  }

  public static Generator<Character> asciiUppercaseChars() {
    return charsInRange('A', 'Z');
  }

  public static Generator<Character> asciiLowercaseChars() {
    return charsInRange('a', 'z');
  }

  public static Generator<Character> asciiLetters() {
    return frequency(9, asciiLowercaseChars(), 1, asciiUppercaseChars()).noShrink();
  }

  public static Generator<Character> digits() {
    return charsInRange('0', '9');
  }

  // strings

  public static Generator<String> stringsOf(@NotNull String possibleChars) {
    List<Character> chars = IntStream.range(0, possibleChars.length()).mapToObj(possibleChars::charAt).collect(Collectors.toList());
    return stringsOf(sampledFrom(chars));
  }

  public static Generator<String> stringsOf(@NotNull Generator<Character> charGen) {
    return listsOf(charGen).map(chars -> {
      StringBuilder sb = new StringBuilder();
      chars.forEach(sb::append);
      return sb.toString();
    });
  }

  public static Generator<String> asciiIdentifiers() {
    return stringsOf(frequency(50, asciiLetters(),
                               5, digits(),
                               1, constant('_')))
      .suchThat(s -> s.length() > 0 && !Character.isDigit(s.charAt(0)));
  }


  // numbers

  public static Generator<Integer> integers() {
    return from(data -> data.drawInt());
  }

  public static Generator<Integer> integers(int min, int max) {
    IntDistribution distribution = IntDistribution.uniform(min, max);
    return from(data -> data.drawInt(distribution));
  }

  public static Generator<Double> doubles() {
    return from(data -> {
      long i1 = data.drawInt();
      long i2 = data.drawInt();
      return Double.longBitsToDouble((i1 << 32) + i2);
    });
  }

  // lists

  public static <T> Generator<List<T>> listsOf(Generator<T> itemGenerator) {
    return from(data -> generateList(itemGenerator, data, data.suggestCollectionSize()));
  }

  public static <T> Generator<List<T>> nonEmptyLists(Generator<T> itemGenerator) {
    return listsOf(itemGenerator).suchThat(l -> !l.isEmpty());
  }

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

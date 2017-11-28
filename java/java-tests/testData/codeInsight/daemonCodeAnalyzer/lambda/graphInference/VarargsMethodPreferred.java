
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class InferenceTest {

  public static void main(String[] args) {
    List<SortedMap<String, Double>> results = codeUnderTest();

    assertThat(results, contains(Stream.of(entry("A", 1.0), entry("B", 2.0), entry("C", 3.0))
                                   .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new))));

    System.out.println("ok!");
  }

  private static List<SortedMap<String, Double>> codeUnderTest() {
    SortedMap<String, Double> result = new TreeMap<>();
    result.put("A", 1.0);
    result.put("B", 2.0);
    result.put("C", 3.0);
    return Collections.singletonList(result);
  }

  private static <K, V> Map.Entry<K, V> entry(K key, V value) {
    return new AbstractMap.SimpleEntry<>(key, value);
  }

  public static <T> void assertThat(T actual, Predicate<? super T> matcher) {
    if (!matcher.test(actual)) throw new AssertionError(String.valueOf(actual));
  }

  @SafeVarargs
  public static <E> Predicate<Iterable<? extends E>> contains(E... items) {
    return Predicate.isEqual(Arrays.asList(items));
  }

}

class InferenceSimplifiedTest {

  public static void main(final Stream<Entry<String, Double>> entry) {
    contains(entry.collect(Collectors.toMap(Entry::getKey, null, null, HashMap::new)));
  }

  @SafeVarargs
  public static <E> void contains(E... items) { }

}
class CyclicReferenceTest {
    void test(Match<String, Integer> match) {
        Match<String, Integer> matcher = match.or(s -> Optional.empty(), i -> 2);
        Match<String, Integer> matcher1 = match.or(s -> s.startsWith("_") ? Optional.of(1) : Optional.empty(), i -> 2);
    }
}

class Match<T, V> {
    public <W> Match<T, V> or(Extractor<T, W> e, Function<W, V> c) {
        return this;
    }
}

interface Extractor<T, W> {
    Optional<W> unapply(T t);
}

interface Function<W, V> {
    public V apply(W t);
}

class Optional<W> {
    public static <T> Optional<T> empty() {
        return null;
    }
  
    public static <T> Optional<T> of(T value) {
        return null;
    }
}
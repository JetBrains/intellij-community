import java.util.*;

interface Factory<T> {
    T create();
}

class LambdaTest {

    public void testR() {
        Map<String, Map<String, Counter>> map =
                new ComputeMap<String, Map<String, Counter>>(() ->
                        new ComputeMap<>(Counter::new));

    }

    public static class ComputeMap<K, V> extends HashMap<K, V> {
        public ComputeMap(Factory<V> factory) {
        }
    }

    public static class Counter {

        public Counter() {
            this(0);
        }

        public Counter(int count) {
        }
    }

}
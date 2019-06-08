
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class MyTest {
    public static <T, E, A, R> CompletableFuture<R> mapCollect(Iterator<T> iterator, Function<T, CompletableFuture<E>> body, Collector<E, A, R> collector) {
        throw new RuntimeException();
    }

    public static void main(String[] args) throws Exception {
        Collection<Pair<String, Integer>> test = new ArrayList<>();
        final CompletableFuture<Map<Object, Object>> future = mapCollect(
                test.iterator(),
                e -> CompletableFuture.completedFuture(new Pair(e.getKey(), e.getValue())),
                Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue()
                )
        );
        future.get();
    }

    public static void main2(String[] args) throws Exception {
        Collection<Pair<String, Integer>> test = new ArrayList<>();
        final CompletableFuture<Map<Object, Object>> future = mapCollect(
                test.iterator(),
                e -> CompletableFuture.completedFuture(Pair.of(e.getKey(), e.getValue())),
                Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue()
                )
        );
        future.get();
    }
    
    public static class Pair<K,V> {
        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
        
        public static <A, B> Pair<A, B> of(A a, B b) {
            return new Pair<A,B>(a, b);
        }

    }
}


import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class MyTest {
    {
        Collector<String, Object, Map<String, String>> collector = Collectors.collectingAndThen(
          toMap(
            a -> <caret>a,
             b -> b
          ),
          m -> m
        );
    }

    private static <T, K, U> Collector<T, ?, Map<K, U>> toMap(
      Function<? super T, ? extends K> keyMapper,
      Function<? super T, ? extends U> valueMapper
    ) {
        return null;
    }
}
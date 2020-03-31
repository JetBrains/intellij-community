import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MyTest {
    void foo(Stream<Map.Entry<Object, Object>> stream) {
        Supplier<Map<Object, Object>> s = null;
        (s = () -> stream
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        Map.Entry::getValue
                ))).get();
        ((Supplier<Map<Object, Object>>) () -> stream
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        Map.Entry::getValue
                ))).get();
    }
}
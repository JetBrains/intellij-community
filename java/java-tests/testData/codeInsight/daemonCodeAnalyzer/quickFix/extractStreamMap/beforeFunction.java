// "Extract variable 'fn' to 'map' operation" "true"
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class Test {
  void testFunction() {
    Stream.of("a", "b", "c").map(x -> {
      Supplier<String> <caret>fn = x::trim;
      return fn.get();
    }).forEach(System.out::println);
  }
}
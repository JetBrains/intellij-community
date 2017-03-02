// "Extract variable 'fn' to separate stream step" "true"
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class Test {
  void testFunction() {
    Stream.of("a", "b", "c").<Supplier<String>>map(x -> x::trim).map(fn -> fn.get()).forEach(System.out::println);
  }
}
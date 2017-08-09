// "Fix all 'Simplify stream API call chains' problems in file" "true"

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

class MyClass {
  void test(String someStr) {

    Predicate<String> predicate = "foo"::equals;
    boolean b = Stream.o<caret>f(someStr).noneMatch(predicate);
    Stream.of(someStr).noneMatch(predicate);
    boolean x = Stream.of(someStr).anyMatch(predicate);
    boolean y = Stream.of(someStr).allMatch(predicate);
    boolean z = Stream.of(someStr).allMatch(unresolved);
    Consumer<String> cons = System.out::println;
    Stream.of(someStr).forEach(cons);

    Optional<String> first = Stream.of(someStr).findFirst();
    Optional<String> joined = Stream.of(someStr).reduce(String::concat);
    Optional<String> max = Stream.of(someStr).max(String.CASE_INSENSITIVE_ORDER);
  }
}

// "Fix all 'Simplify stream API call chains' problems in file" "true"

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

class MyClass {
  void test(String someStr) {

    Predicate<String> predicate = "foo"::equals;
    boolean b = !predicate.test(someStr);
    Stream.of(someStr).noneMatch(predicate);
    boolean x = predicate.test(someStr);
    boolean y = predicate.test(someStr);
    boolean z = Stream.of(someStr).allMatch(unresolved);
    Consumer<String> cons = System.out::println;
    cons.accept(someStr);

    Optional<String> first = Optional.of(someStr);
    Optional<String> joined = Optional.of(someStr);
    Optional<String> max = Optional.of(someStr);
  }
}

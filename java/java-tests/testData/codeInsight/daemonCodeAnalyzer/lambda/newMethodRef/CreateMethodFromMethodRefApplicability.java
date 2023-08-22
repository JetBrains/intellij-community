
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DemoApplicationTests {

  void test2() {
    var a = Stream.of("")
      .map(s -> s.substring(1))
      .map(s -> s.length())
      .map(l1 -> String.valueOf(l1)).collect(Collectors.toList());

    Stream.of("")
      .map(l -> a.get(l.length()))
      .map(s -> new DemoApplicationTests()).findAny()
      .map(d -> d.foo(this::<error descr="Reference to 'bar' is ambiguous, both 'bar(long)' and 'bar(int)' match">bar</error>));
  }

  <T> void foo(Consumer<T> c) {}

  <T, R> void foo(Function<T, R> c) {}

  void bar(long j) {}

  void bar(int i) {}
}

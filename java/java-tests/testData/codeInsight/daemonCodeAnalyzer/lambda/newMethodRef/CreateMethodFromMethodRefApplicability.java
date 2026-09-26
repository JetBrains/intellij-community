
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
      .map(d -> d.foo<error descr="Ambiguous method call: both 'DemoApplicationTests.foo(Consumer<Object>)' and 'DemoApplicationTests.foo(Function<Object, Object>)' match">(this::bar)</error>);
  }

  <T> void foo(Consumer<T> c) {}

  <T, R> void foo(Function<T, R> c) {}

  void bar(long j) {}

  void bar(int i) {}
}

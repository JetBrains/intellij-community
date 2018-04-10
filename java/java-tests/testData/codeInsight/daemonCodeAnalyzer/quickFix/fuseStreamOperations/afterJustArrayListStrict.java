// "Fix all 'Subsequent steps can be fused into Stream API chain' problems in file" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  interface Foo {
  }

  // IDEA-179303
  void test1(Stream<Foo> fooStream) {
    ArrayList<Foo> collectedFoos = fooStream.collect(Collectors.toCollection(ArrayList::new));
  }

  void test2(Stream<Foo> fooStream) {
    List<Foo> collectedFoos = fooStream.collect(Collectors.toCollection(ArrayList::new));
  }
}
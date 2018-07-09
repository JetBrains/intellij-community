// "Fix all 'Subsequent steps can be fused into Stream API chain' problems in file" "true"
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  interface Foo {
  }

  // IDEA-179303
  void test1(Stream<Foo> fooStream) {
    ArrayList<Foo> collectedFoos = new ArrayList<>(fooStream.co<caret>llect(Collectors.toList()));
  }

  void test2(Stream<Foo> fooStream) {
    List<Foo> collectedFoos = new ArrayList<>(fooStream.collect(Collectors.toList()));
  }
}
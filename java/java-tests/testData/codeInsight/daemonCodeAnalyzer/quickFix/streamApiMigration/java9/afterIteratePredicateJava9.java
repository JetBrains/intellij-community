// "Replace with forEach" "true"

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class Main {
  static class A {
    A next(){return null;}
    int x;
  }

  static boolean isGood(A a) {}

  public long test() {
      Stream.iterate(new A(), (UnaryOperator<A>) Main::isGood, a -> a.next()).filter(a -> a.x < 3).forEach(System.out::println);
  }
}
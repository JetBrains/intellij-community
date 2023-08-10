// "Collapse loop with stream 'forEach()'" "true-preview"

import java.util.stream.Stream;

public class Main {
  static class A {
    A next(){return null;}
    int x;
  }

  static boolean isGood(A a) {}

  public long test() {
      Stream.iterate(new A(), a -> isGood(a), a -> a.next()).filter(a -> a.x < 3).forEach(System.out::println);
  }
}
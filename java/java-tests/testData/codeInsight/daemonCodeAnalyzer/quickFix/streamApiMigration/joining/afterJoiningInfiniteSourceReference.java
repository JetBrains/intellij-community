// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {
  B next() {return null;}
  int x;
}

class B extends A {}


public class Main {
  public static int find(List<List<String>> list) {
    String sb = Stream.iterate(new A(), A::next).filter(a -> a.x % 100 == 0).map(a -> String.valueOf(a.x)).collect(Collectors.joining());
  }
}
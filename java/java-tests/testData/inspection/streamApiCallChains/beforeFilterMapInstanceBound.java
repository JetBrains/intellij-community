// "Swap 'filter()' and 'map()'" "false"

import java.util.stream.Stream;

class X {
  boolean foo(X... args) {
    return args.length > 0;
  }

  void bar() {
    X x1 = new X();
    Stream.of(new X()).filter(x -> x.foo()).map<caret>(x1::foo).forEach(System.out::println);
  }
}

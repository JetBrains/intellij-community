// "Swap 'filter()' and 'map()'" "false"

import java.util.stream.Stream;

class X {
  static boolean foo(X... args) {
    return args.length > 0;
  }

  void bar() {
    Stream.of(new X()).filter(x -> x.foo()).map<caret>(X::foo).forEach(System.out::println);
  }
}

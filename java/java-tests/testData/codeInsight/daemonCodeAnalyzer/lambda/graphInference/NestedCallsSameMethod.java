import java.util.List;

import java.util.function.Function;

abstract class Main2 {
  void address(Foo sa) {
    String ds = foobar(foobar(sa, Foo::getBar), Bar ::getName);
    Function<Foo, Bar> f = null;
    String ds1 = foobar(foobar(sa, f), null);
  }

  abstract <T, V> V foobar(T t, Function<T, V> mapper);

  class Foo {
    Bar getBar() {
      return new Bar();
    }
  }

  class Bar {
    String getName(){
      return null;
    }
  }
}


class Main0 {
  <T> List<T> foo(T t){
    return null;
  }

  {
    foo(foo(""));
  }
}


class Main {
    static <T> T foo(T t) { return null; }

    static {
        long l1 = foo(foo(1));
        Integer i = 1;
        long l2 = foo(foo(i));
    }
}

class Main1 {
  static <T> T foo(long t) { return null;}

  static <B> B bar(B t) { return null;}

  static {
    //long l = foo(bar(1));
  }
}

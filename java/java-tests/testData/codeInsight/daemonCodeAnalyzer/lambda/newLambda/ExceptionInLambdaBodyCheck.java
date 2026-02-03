
import java.util.function.Supplier;
import java.io.IOException;

interface A {
}

interface B extends A {
}

interface D<TT extends Exception> {
  <T extends A, E extends TT> T foo() throws E;
}

class E {

  void bar(D<RuntimeException> d) {
    foobar(supplier(() -> {
      try {
        return d.foo();
      } catch (RuntimeException e) {
        throw e;
      }
    }));
    foobar(supplier(() -> {
      return d.foo();
    }));

    foobar(supplier(() -> d.foo()));
    foobar(supplier(d::foo));

    foobar(supplier(() -> {
      throw new RuntimeException();
    }));
  }

  <T> Supplier<T> supplier(Supplier<T> s) {
    return s;
  }

  void foobar(Supplier<B> s) {
  }

}



class Test {
  interface A {
  }

  interface B extends A {
  }

  interface D {
    <T extends A> T foo() throws IOException;
  }

  class E {

    void bar(D d) {
      foobar(supplier(() -> {
        try {
          return d.foo();
        } catch (IOException e) {
          throw new RuntimeException();
        }
      }));
    }

    <T> Supplier<T> supplier(Supplier<T> s) {
      return s;
    }

    void foobar(Supplier<B> s) {
    }
  }
}


class TestNoTypeParameterBounds {

  interface A {
  }

  interface B extends A {
  }

  interface D {
    <T extends A, E extends Throwable> T foo() throws E;
  }

  class E {

    void bar(D d) {
      foobar(supplier(() -> {
        try {
          return d.foo();
        } catch (RuntimeException e) {
          throw e;
        }
      }));
      foobar(supplier(() -> {
        return d.foo();
      }));

      foobar(supplier(() -> d.foo()));
      foobar(supplier(d::foo));

      foobar(supplier(() -> {
        throw new RuntimeException();
      }));
    }

    <T> Supplier<T> supplier(Supplier<T> s) {
      return s;
    }

    void foobar(Supplier<B> s) {
    }

  }
}
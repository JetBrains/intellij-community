import java.util.Arrays;

import static java.lang.System.out;

final class LambdaMain {
  public static void main(final String... args) {
    for (final A<String, X> a : Arrays.<A<String, X>>asList(new A<String, X>() {
      @Override
      public String foo(final String ignored)
        throws X {
        throw new X();
      }
    }, ignored -> { throw new X(); }))
      try {
        test(a, "Bob");
      } catch (final X x) {
        x.printStackTrace();
      }

    try {
      out.println(test(new A<String, X>() {
        @Override
        public String foo(final String ignored)
          throws X {
          throw new X();
        }
      }, "Bob"));
    } catch (final Exception e) {
      e.printStackTrace();
    }

    try {
      out.println(test(ignored -> { throw new X(); }, "Bob"));
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  static class X
    extends Exception {}

  interface A<T, E extends Exception> {
    T foo(T ignored)
      throws E;
  }

  static <T, E extends Exception> T test(final A<T, E> a, final T ignored)
    throws E {
    return a.foo(ignored);
  }
}

final class LambdaMainTest {
  public void main(A<String> a) {
    println(test(a));
  }


  public void println(boolean x) {}

  public void println(String x) {}



  interface A<T> {
    T foo(T ignored) ;
  }

  static <T> T test (final A<T> a) {
    return null;
  }
}
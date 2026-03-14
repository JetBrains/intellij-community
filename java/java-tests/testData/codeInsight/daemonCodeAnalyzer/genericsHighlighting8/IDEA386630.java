import java.util.function.Function;

class A {
  private <T extends Number> A(T t) {}

  private static <T extends Number> void use(T t) {}

  static {
    new <<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>>A();
    A.<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>>use();

  }
}

class B {
  static {
    new <error descr="Wrong number of type arguments: 2; required: 1">< String , Number ></error> <error descr="'A(java.lang.String)' has private access in 'A'">A</error> ( ) ;
    A . <error descr="Wrong number of type arguments: 2; required: 1">< String , Number ></error> <error descr="'use(java.lang.String)' has private access in 'A'">use</error> ( ) ;
  }
}


class C {
  static {
    <error descr="Inferred type 'B' for type parameter 'B' is not within its bound; should extend 'C.Builder<C.Alfa>'">new C(C::string)</error>;
    <error descr="Inferred type 'B' for type parameter 'B' is not within its bound; should extend 'C.Builder<C.Alfa>'">C.use(C::string)</error>;
  }

  private static <T extends Alfa, B extends Builder<T>> void use(final Function<B, String> f1) {}

  private <T extends Alfa, B extends Builder<T>> C(final Function<B, String> f1) {}

  private static String string(final Builder<? extends Beta> builder) {
    return "";
  }

  static class Alfa {}
  static class Beta extends Alfa {}

  static class Builder<T> {}
}
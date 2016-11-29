import java.util.function.Supplier;

enum E {
  A(  (<warning descr="Casting 'E::f' to 'Supplier<Object>' is redundant">Supplier<Object></warning>)E::f),
  B(  (Supplier<String>)E::f),
  ;
  <T> E(Supplier<T> s){}

  static <K> K f() {
    return null;
  }
}
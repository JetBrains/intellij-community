@javax.annotation.ParametersAreNonnullByDefault
interface I {
  void foo(Object o);
}

@javax.annotation.ParametersAreNonnullByDefault
final class A implements I {
  public void foo(Object o) {
    o.toString();
  }
}
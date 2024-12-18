import java.util.Optional;

class AAA {
  static final class A {}
  static class B extends <error descr="Cannot inherit from final class 'AAA.A'">A</error> {}
  static class C extends B {}
  static class D extends B {}

  public static void main(boolean f, C c, D d) {
    Optional.of(f ? c : d).get();
  }
}
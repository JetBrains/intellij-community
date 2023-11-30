import java.util.Objects;

class T {
  static class A {
    String s;
  }
  A a;
  static boolean same(T t, String s) {
    return Objects.equals(s, t.a.s);
  }
}
import java.util.Objects;

class T {
  String s;
  static class X extends T {
    boolean same(String s) {
      return Objects.equals(super.s, s);
    }
  }
}
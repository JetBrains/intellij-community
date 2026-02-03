import java.util.Objects;

class T {
  String s;
  class A {
    boolean notSame(String s) {
      return !Objects.equals(T.this.s, s);
    }
  }
}
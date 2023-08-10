import java.util.Objects;

class T {
  static boolean same(String t, String s) {
    return Objects.equals(t + "a", s);
  }
}
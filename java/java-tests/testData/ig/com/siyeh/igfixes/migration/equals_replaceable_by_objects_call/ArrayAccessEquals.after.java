import java.util.Objects;

class T {
  static boolean same(String[] t, String[] s, int i) {
    return Objects.equals(s[i], t[i]);
  }
}
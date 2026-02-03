import java.util.Objects;

class T {
  String s;
  boolean same(String s) {
    return Objects.equals(this.s, s);
  }
}
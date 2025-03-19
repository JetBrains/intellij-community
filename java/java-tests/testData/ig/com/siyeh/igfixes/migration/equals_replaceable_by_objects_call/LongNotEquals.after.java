import java.util.Objects;

class T {
  static class P { String n; }

  static boolean notSame(P a, P b) {
    return !Objects.equals(a.n, b.n);
  }
}
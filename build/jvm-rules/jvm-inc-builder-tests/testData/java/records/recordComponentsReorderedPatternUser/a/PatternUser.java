public class PatternUser {
  int f(Object o) {
    if (o instanceof Rec(int x, String s)) {
      return x;
    }
    return 0;
  }
}

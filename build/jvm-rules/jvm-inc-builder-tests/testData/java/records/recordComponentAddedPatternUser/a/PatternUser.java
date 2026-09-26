public class PatternUser {
  int f(Object o) {
    if (o instanceof Rec(int x)) {
      return x;
    }
    return 0;
  }
}

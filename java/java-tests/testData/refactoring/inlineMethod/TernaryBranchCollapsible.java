import java.util.function.Predicate;

class X {
  int x = Math.random() > 0.5 ? te<caret>st(1, 2, 3) + 1 : 0;

  int test(int a, int b, int c) {
    return (a * a + b * b) / (c * c);
  }
}
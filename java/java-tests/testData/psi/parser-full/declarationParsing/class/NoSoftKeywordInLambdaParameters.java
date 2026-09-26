public class ValueInLambda {
  void test() {
    Fn fn = value -> value;
    Fn fn2 = (value) -> value;
    Fn fn3 = (int value) -> value;
    Fn fn4 = sealed -> sealed;
  }

  interface Fn {
    int x(int y);
  }
}
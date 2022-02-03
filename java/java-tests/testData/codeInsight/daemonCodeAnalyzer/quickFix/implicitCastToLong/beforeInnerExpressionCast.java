// "Fix all 'Integer multiplication or shift implicitly cast to 'long'' problems in file" "true"
class Test {
  void test(int a, int b) {
    long c = a * (<caret>a * b);
    long d = (a * (b * (a * (b * 2)));
    long e = -(-a * -(-a * -b));
    long f = a * ((a == 2) ? b * a : a);
    long g = a + (a + ((a + b) * (a + b)));
    long h = a << (a * b);
    long i = (a * b) << (b * a);
  }
}
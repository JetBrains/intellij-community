// "Fix all 'Integer multiplication or shift implicitly cast to 'long'' problems in file" "true"
class Test {
  void test(int a, int b) {
    long c = <caret>a * 2;
    long d = 2 * a;
    long d1 = -2 * a;
    long e = a * b;
    long f = a * b * 2; // should be converted to (long) a * b * 2 or 2L * a * b (but not a*b*2L: in this case a*b would still be integer)
    long g = a << 2;
    long h = 2 << a;
    long i = (2) * a;
  }
}
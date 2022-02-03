// "Fix all 'Integer multiplication or shift implicitly cast to 'long'' problems in file" "true"
class Test {
  void test(int a, int b) {
    long c = a * 2L;
    long d = 2L * a;
    long d1 = -2L * a;
    long e = (long) a * b;
    long f = (long) a * b * 2; // should be converted to (long) a * b * 2 or 2L * a * b (but not a*b*2L: in this case a*b would still be integer)
    long g = (long) a << 2;
    long h = 2L << a;
    long i = (2L) * a;
  }
}
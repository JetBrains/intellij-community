// "Fix all 'Integer multiplication or shift implicitly cast to 'long'' problems in file" "true"
class Test {
  void test(int a, int b) {
    long c = a * ((long) a * b);
    long d = (a * (b * (a * (b * 2L)));
    long e = -(-a * -((long) -a * -b));
    long f = a * ((a == 2) ? (long) b * a : a);
    long g = a + (a + ((long) (a + b) * (a + b)));
    long h = (long) a << (a * b);
    long i = ((long) a * b) << (b * a);
  }
}
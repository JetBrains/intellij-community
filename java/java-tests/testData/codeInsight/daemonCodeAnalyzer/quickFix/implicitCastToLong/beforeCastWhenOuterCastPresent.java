// "Fix all 'Integer multiplication or shift implicitly cast to 'long'' problems in file" "true"
class Test {

  void test (int i1, int i2, int i3) {
    long a = (long) ((<caret>i1 * i2) * i3);
    long b = (int) ((i1 * i2) * i3); // shouldn't convert because result is still casted to int
    long c = ((int) i1 * i2 * i3);
    long d = ((long) i1 * i2 * i3); // fix is not needed at all
    long e = (long) (i1 * i2);
    long f = (i2) * (long) i1; // fix is not needed at all
    long g = (long) i1 * (i2 * i3);
  }
}
class Test {

  void test(int i) {
    int j = 42;
    double d = 42.0;
    <warning descr="Division result is truncated to integer">j *= 2/3</warning>;
    <warning descr="Division result is truncated to integer">j /= 3/2</warning>;
    <warning descr="Division result is truncated to integer">j *= i/2</warning>;
    j *= d / 2;
    <warning descr="Division result is truncated to integer">j *= 'a' / 2</warning>;

    i *= 2;
    j *= i / 2; // should not warn: we know that i is always even at this point
    i /= 2;
    <warning descr="Division result is truncated to integer">j *= i / 2</warning>; // should warn again
  }
}
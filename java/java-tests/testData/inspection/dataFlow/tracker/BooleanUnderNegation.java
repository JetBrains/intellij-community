/*
Value is always false (b)
  'b == false' was established from condition (!b)
 */
class Test {
  void test(boolean b, int limit) {
    for(int i=0; i<=limit; i++) {
      if (!b) System.out.println(<selection>b</selection>);
    }
  }
}
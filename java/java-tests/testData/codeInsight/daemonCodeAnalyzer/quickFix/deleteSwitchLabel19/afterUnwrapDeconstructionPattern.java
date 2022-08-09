// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        int i = r.i();
        String str = r.s();
        System.out.println(i);
        i = 42;
        System.out.println(str + i);
    }

  record R(int i, String s, double ignored) {
  }
}
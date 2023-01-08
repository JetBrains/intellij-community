// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        System.out.println(r.s() + r.i());
        System.out.println(r);
    }

    record R(int i, String s) {}
}
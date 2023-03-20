// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        System.out.println(r.s() + r.i());
    }

    record R(int i, String s) {}
}
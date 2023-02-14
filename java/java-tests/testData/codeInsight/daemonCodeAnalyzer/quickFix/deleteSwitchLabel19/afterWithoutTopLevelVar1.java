// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        System.out.println(r.s());
    }

    record R(int i, String s) {}
}
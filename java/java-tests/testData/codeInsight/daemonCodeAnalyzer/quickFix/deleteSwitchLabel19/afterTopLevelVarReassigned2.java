// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        R rec = r;
        rec = new R(42, "hello");
        System.out.println(rec.s() + rec.i());
        System.out.println(rec);
    }

    record R(int i, String s) {}
}
// "Remove unreachable branches" "true"
class Test {
    void test(Object obj) {
        if (!(obj instanceof R)) return;
        R rec = (R) obj;
        rec = new R(42, "hello");
        System.out.println(rec.s() + rec.i());
    }

    record R(int i, String s) {}
}
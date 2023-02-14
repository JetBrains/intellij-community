// "Remove unreachable branches" "true"
class Test {
    void test(Object obj) {
        if (!(obj instanceof R)) return;
        R r = (R) obj;
        System.out.println(r.i() + r.s());
    }

    record R(int i, String s) {}
}
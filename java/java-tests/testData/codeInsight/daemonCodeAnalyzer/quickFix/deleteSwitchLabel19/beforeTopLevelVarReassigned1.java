// "Remove unreachable branches" "true"
class Test {
    void test(Object obj) {
        if (!(obj instanceof R)) return;
        switch (obj) {
            case R(int i, String s)<caret> rec when true:
                rec = new R(42, "hello");
                System.out.println(s + i);
                break;
            default:
                break;
        }
    }

    record R(int i, String s) {}
}
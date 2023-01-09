// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        switch (r) {
            case R(int i, String s)<caret> rec when true:
                rec = new R(42, "hello");
                System.out.println(s + i);
                System.out.println(rec);
                break;
        }
    }

    record R(int i, String s) {}
}
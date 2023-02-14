// "Remove unreachable branches" "true"
class Test {
    void test(R r) {
        switch (r) {
            case R(int i, String s)<caret> when true:
                System.out.println(s);
                break;
        }
    }

    record R(int i, String s) {}
}
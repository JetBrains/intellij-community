// "Remove unreachable branches" "true"
class Test {
    void test(Object obj) {
        if (!(obj instanceof R)) return;
        switch (obj) {
            case R(int i, String s)<caret> when true:
                System.out.println(i + s);
                break;
        }
    }

    record R(int i, String s) {}
}
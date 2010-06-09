public class Test {
    public static final int FOO = 0;
    public static final int BAR = 1;

    void foo() {
        int i = foobar(false);
        switch (i) {
            case FOO:
                break;
            case BAR:
                break;
        }
    }

    int foobar(boolean flag) {
        if (flag) {
            return FOO;
        }
        return BAR;
    }
}
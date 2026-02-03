public class Test {
    public static final int FOO = 0;
    public static final int BAR = 1;

    void foo(int i) {
        switch (i) {
            case FOO:
                break;
            case BAR:
                break;
            case 8:
                break;
        }
    }

    void foobar() {
        foo(FOO);
        foo(BAR);
    }
}
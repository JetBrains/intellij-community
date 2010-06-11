public class Test {
    public static final int FOO = 0;
    public static final int BAR = 1;

    void foo(int i) {
        switch (i) {
            case FOO:
                break;
            case BAR:
                break;
        }
    }

    void foobar() {
        int max = Math.max(FOO, BAR);
        foo(max);
    }
}
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
        int k = Math.max(i * i, i + i);
    }
}
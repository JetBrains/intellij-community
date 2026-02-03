public class Test {
    public static final int FOO = 0;
    public static final int BAR = 2;

    void foo(String[] args) {
        switch (boo(args)) {
            case FOO:
                break;
            case BAR:
                break;
        }
    }

    int boo(String[] args) {
        return args.length;
    }
}
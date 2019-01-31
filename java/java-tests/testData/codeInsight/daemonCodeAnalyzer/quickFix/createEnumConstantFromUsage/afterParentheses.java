// "Create enum constant 'A'" "true"

public enum E {
    A;

    public static final Foo QQQ;

    static {
        QQQ = new Foo((A.BAR));
    }
}

class Bar {
    public static final int BAR = 0;
}
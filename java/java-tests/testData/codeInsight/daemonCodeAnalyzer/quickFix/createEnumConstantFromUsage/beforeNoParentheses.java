// "Create enum constant 'A'" "true"

public enum E {;

    public static final Foo QQQ;

    static {
        QQQ = new Foo(<caret>A.BAR);
    }
}

class Bar {
    public static final int BAR = 0;
}
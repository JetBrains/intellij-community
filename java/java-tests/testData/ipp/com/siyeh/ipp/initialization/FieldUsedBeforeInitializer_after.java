class Foo  {
    static final int f1;

    static {
        f1 = 2;
    }

    static final int f2 = f1 + 1;

    static {
        System.out.println();
    }
}
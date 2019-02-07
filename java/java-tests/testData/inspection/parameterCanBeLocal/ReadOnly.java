class Test {
    public static final boolean myFoo = false;

    public static void foo(int p) {
        if (!myFoo) return;
        System.out.println("p = " + p);
    }
}

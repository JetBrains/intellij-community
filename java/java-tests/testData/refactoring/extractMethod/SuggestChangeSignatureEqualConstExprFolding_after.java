class C {
    public static final int A = 4 - 1;
    public static final int B = 4 + 2;

    void foo(int x) {

        newMethod(x, A + 1);


        newMethod(x, B - 2);
    }

    private void newMethod(int x, int i) {
        if (x == i)
            bar(i);
    }

    void bar(int n) {}
}
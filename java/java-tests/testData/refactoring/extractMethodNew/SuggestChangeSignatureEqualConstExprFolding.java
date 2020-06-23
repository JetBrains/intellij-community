class C {
    public static final int A = 4 - 1;
    public static final int B = 4 + 2;

    void foo(int x) {
        <selection>
        if(x == A + 1)
            bar(A + 1);
        </selection>

        if(x == B - 2)
            bar(B - 2);
    }

    void bar(int n) {}
}
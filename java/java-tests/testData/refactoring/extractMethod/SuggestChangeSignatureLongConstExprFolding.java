class C {
    public static final int A = 1, B = 2, C = 3, D = 4, E = 5;

    void foo(int x) {
        <selection>
        if (x >= A + B + C + D + E + 1)
            bar("A" + "B" + "C" + "D" + "E" + 1);
        </selection>

        if (x >= A + B + C + D + E + 2)
            bar("A" + "B" + "C" + "D" + "E" + 2);

        if (x <= A + B + C + D + E + 2)
            bar("A" + "B" + "C" + "D" + "E" + 2);
    }

    void bar(String s) {}
}
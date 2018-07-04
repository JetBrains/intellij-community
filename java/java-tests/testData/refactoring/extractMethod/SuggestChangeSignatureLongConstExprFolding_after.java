class C {
    public static final int A = 1, B = 2, C = 3, D = 4, E = 5;

    void foo(int x) {

        newMethod(x, 1);


        newMethod(x, 2);

        if (x <= A + B + C + D + E + 2)
            bar("A" + "B" + "C" + "D" + "E" + 2);
    }

    private void newMethod(int x, int i) {
        if (x >= A + B + C + D + E + i)
            bar("A" + "B" + "C" + "D" + "E" + i);
    }

    void bar(String s) {}
}
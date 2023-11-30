class D {
    void f(int x, int y, boolean b) {
        final boolean sss = <caret>b ? x > y : x <= y;
    }
}
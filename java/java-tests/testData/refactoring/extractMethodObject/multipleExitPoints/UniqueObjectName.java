class A {
    int foo() {
        int x = 45;
        int y = 46;
        int inner = 47;
        <selection>x = y + 1;
        y = 77;</selection>
        x = y + x + 45;
        boolean z = true;
        if (z) {
            return y;
        } else {
            x = y;
        }
        y = 45;
        return x;
    }
}
class VarArgs {
    private void g() {
        f<flown111>(<flown1111>1);
        f<flown112>(<flown1121>2, <flown1122>3, <flown1123>4);
    }

    private void f(int... <flown11>ints) {
        int <caret>p = <flown1>ints[0];
    }
}

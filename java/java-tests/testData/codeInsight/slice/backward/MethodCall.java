class T2 {
    void f() {
        int i = <caret>x();
    }

    private int x() {
        return <flown1>0;
    }
}
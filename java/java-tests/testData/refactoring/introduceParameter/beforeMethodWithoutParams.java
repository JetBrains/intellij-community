class Test {
    int m() {
        return <selection>0</selection>;
    }
}

class X3 {
    int n() {
        Test t;
        return t.m();
    }
}
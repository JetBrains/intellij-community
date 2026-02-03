class Test {
    public int i;
    
    public int getI() { return i; }
    
    int method(int a) {
        return <selection>a + i</selection>;
    }
}

class XXX {
    public int m() {
        Test t;
        return t.method(1);
    }
}
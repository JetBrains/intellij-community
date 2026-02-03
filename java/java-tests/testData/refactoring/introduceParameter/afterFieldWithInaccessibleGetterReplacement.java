class Test {
    public int i;
    
    public int getI() { return i; }
    
    int method(int a, int anObject) {
        return anObject;
    }
}

class XXX {
    public int m() {
        Test t;
        return t.method(1, 1 + t.i);
    }
}
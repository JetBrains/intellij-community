class Test {
    public static int i;
    
    int method(int a) {
        return <selection>a + i</selection>;
    }
}

class X {
    public static int i;
    
    int yyy(int z) {
        Test t;
        return t.method(z);
    }
}
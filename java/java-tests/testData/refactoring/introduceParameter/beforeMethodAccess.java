class Test {
    int method(int a, int b) {
        return <selection>anotherMethod(a + b)</selection>;
    }
    int i;
    
    int anotherMethod(int x) { 
        return x;
    }
}

class XTest {
    int n() {
        Test t;
        
        return t.method(1, 2);
    }
}
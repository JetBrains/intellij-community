class Test {
    int method(int a, int b, int anObject) {
        return anObject;
    }
    int i;
    
    int anotherMethod(int x) { 
        return x;
    }
}

class XTest {
    int n() {
        Test t;
        
        return t.method(1, 2, t.anotherMethod(1 + 2));
    }
}
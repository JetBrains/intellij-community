class Test {
    int method(int a, int b, Test anObject) {
        return anObject.i;
    }
    private int i;
}

class XTest {
    int n() {
        Test t;
        
        return t.method(1, 2, t);
    }
}
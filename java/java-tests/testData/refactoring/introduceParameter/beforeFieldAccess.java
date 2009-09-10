class Test {
    int method(int a, int b) {
        return <selection>i</selection>;
    }
    int i;
}

class XTest {
    int n() {
        Test t;
        
        return t.method(1, 2);
    }
}
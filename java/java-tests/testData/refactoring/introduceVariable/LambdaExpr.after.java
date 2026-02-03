interface SAM<X> {
        X m(int i, int j);
    }

class Foo {  
    void test() {
        SAM<Integer> c = (i, j) -> i + j;
        m(c);
    }
    void m(SAM<Integer> s) { }
}
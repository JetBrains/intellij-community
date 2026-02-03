interface SAM<X> {
        X m(int i, int j);
    }

class Foo {  
    void test() {
        m(<selection>(i, j) -> i + j</selection>);
    }
    void m(SAM<Integer> s) { }
}
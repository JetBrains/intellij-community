interface SAM<X> {
        X m(int i, int j);
    }

class Foo {  
    void test() {
        SAM<Integer> c = (i, j) -> i + j;
        SAM<Integer> s3 = m(c);
    }
    <X> SAM<X> m(SAM<X> s) { return null; }
}
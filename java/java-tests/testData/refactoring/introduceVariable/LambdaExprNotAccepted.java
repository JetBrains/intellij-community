interface SAM<X> {
        X m(int i, int j);
    }

class Foo {  
    void test() {
      SAM<Integer> s3 = m(<selection>(i, j) -> i + j</selection>);
    }
    <X> SAM<X> m(SAM<X> s) { return null; }
}
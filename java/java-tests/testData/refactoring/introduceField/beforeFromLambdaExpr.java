interface SAM<X> {
        X m(int i, int j);
    }

class Foo {  
    void test() {
        m((i, j) -> {
          <selection>0</selection>
          return i + j;
        });
    }
    void m(SAM<Integer> s) { }
}
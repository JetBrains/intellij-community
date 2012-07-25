interface SAM<X> {
        X m(int i, int j);
    }

class Foo {
    public final int anInt = 0;

    void test() {
        m((i, j) -> {
            return i + j;
        });
    }
    void m(SAM<Integer> s) { }
}
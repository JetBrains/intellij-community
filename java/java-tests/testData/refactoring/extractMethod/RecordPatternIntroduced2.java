class Foo {
    void test(Object obj) {
        if (<selection>obj instanceof Point(int x, int y) && x == y</selection>) {
            System.out.println(x);
        }
    }

    record Point(int x, int y) {}
}
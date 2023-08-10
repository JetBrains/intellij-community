class Foo {
    void test(Object obj) {
        if (<selection>obj instanceof Point(int x, int y)</selection> && x == y) {
            System.out.println("hello");
        }
    }

    record Point(int x, int y) {}
}
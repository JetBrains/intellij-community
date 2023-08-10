class Foo {
    void test(Object obj) {
        if (<selection>obj instanceof Point(int x, int y) point</selection> && x == y) {
            System.out.println(point.x());
        }
    }

    record Point(int x, int y) {}
}
class Foo {
    void test(Object obj) {
        if (newMethod(obj)) {
            System.out.println("hello");
        }
    }

    private boolean newMethod(Object obj) {
        return obj instanceof Point(int x, int y) point && x == y;
    }

    record Point(int x, int y) {}
}
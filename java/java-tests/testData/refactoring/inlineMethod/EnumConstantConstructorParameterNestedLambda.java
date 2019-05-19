class Test {
    enum Foo {
        BAR(<caret>getTwice(() -> {
            return getNum();
        }));

        Foo(int val) {

        }
    }

    static int getNum() {
        return 4;
    }

    static int getTwice(IntSupplier fn) {
        int x = fn.getAsInt();
        int y = fn.getAsInt();
        return x + y;
    }
}

class FooBar {
    private static final class Bar2 {
        private Bar2() {
        }
    }

    private interface I<T> {
        T create();
    }

    static void foo(I intf) {}


    public static void main(String[] args) throws Exception {
        foo(() -> new Bar2());
    }
}
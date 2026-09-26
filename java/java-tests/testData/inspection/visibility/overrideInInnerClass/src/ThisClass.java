class Foo {
    void foo() {}
    class Bar extends Foo {
        @java.lang.Override
        void foo() {
            super.foo();
        }
    }
}
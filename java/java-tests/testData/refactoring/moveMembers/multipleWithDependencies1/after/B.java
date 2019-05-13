public class B {
    static void foo() {
    }

    static class Bar {
        void foo() {
            B.foo();
        }
    }
}

class Outer {
    static class Inner {
        static void foo() {}
    }

    {
        Runnable r = Inner::foo;
    }
}

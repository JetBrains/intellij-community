class Outer {
    static void foo() {}

    static class Inner {
    }

    {
        Runnable r = Outer::foo;
    }
}

interface SAM {
    default void foo() {
        foo(false);
    }

    void foo(boolean b);
}
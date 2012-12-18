interface I {
    default void foo () {
        newMethod();
    }

    private default void newMethod() {
        System.out.println("hello");
    }
}
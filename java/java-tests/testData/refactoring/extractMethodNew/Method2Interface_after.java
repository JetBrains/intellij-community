interface I {
    default void foo () {
        newMethod();
    }

    default void newMethod() {
        System.out.println("hello");
    }
}
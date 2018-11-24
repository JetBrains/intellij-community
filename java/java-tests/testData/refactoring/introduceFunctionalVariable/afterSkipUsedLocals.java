class Test {
    void foo(String name) {
        System.out.println("Hello, ");
        Runnable runnable = () -> System.out.println(name);
        runnable.run();
    }
}
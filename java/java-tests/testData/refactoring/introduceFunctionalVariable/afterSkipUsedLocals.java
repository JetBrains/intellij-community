class Test {
    void foo(String name) {
        System.out.println("Hello, ");
        Runnable runnable = new Runnable() {
            public void run() {
                System.out.println(name);
            }
        };
        runnable.run();
    }
}
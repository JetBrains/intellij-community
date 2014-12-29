interface I {
    static void foo () {
        newMethod();
    }

    private static void newMethod() {
        System.out.println("hello");
    }
}